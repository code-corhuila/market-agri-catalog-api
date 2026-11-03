package co.edu.corhuila.marketagri.catalog.application.usecase;

import co.edu.corhuila.marketagri.catalog.application.port.in.ReleaseStock;
import co.edu.corhuila.marketagri.catalog.application.port.in.ReserveStock;
import co.edu.corhuila.marketagri.catalog.application.port.out.ProductStock;
import co.edu.corhuila.marketagri.catalog.application.port.out.ReservationRepository;
import co.edu.corhuila.marketagri.catalog.application.port.out.UnitOfWork;
import co.edu.corhuila.marketagri.catalog.domain.model.Product;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductNotFoundException;
import co.edu.corhuila.marketagri.catalog.domain.model.Quantity;
import co.edu.corhuila.marketagri.catalog.domain.model.ReservationMismatchException;
import co.edu.corhuila.marketagri.catalog.domain.model.StockReservation;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * I-03 and I-04. The rule of the stock stays in {@link Product}; this class only orders the steps
 * inside one {@link UnitOfWork}. Atomicity comes from the lock of
 * {@link ProductStock#findByIdForUpdate}: concurrent reservations of a product run one after
 * the other, each one reading the quantity the previous one left.
 */
public final class StockReservationService implements ReserveStock, ReleaseStock {

    private final ProductStock stock;
    private final ReservationRepository reservations;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public StockReservationService(ProductStock stock, ReservationRepository reservations,
                                   UnitOfWork unitOfWork, Clock clock) {
        this.stock = stock;
        this.reservations = reservations;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    @Override
    public Reservation reserve(UUID productId, UUID transactionId, BigDecimal requested) {
        Quantity quantity = Quantity.requested(requested);
        return unitOfWork.inTransaction(() -> {
            Product product = stock.findByIdForUpdate(productId)
                    .orElseThrow(() -> new ProductNotFoundException(productId));
            Optional<StockReservation> earlier = reservations.findByTransactionId(transactionId);
            if (earlier.isPresent()) {
                return retry(earlier.get(), productId, quantity);
            }
            Instant now = clock.instant();
            product.reserve(quantity, now);
            stock.updateStock(product);
            StockReservation reservation = StockReservation.of(transactionId, product, quantity, now);
            // Only a race with the same transactionId on another product gets here: rolls back the stock.
            if (!reservations.add(reservation)) {
                throw new ReservationMismatchException(transactionId);
            }
            return new Reservation(reservation, true);
        });
    }

    @Override
    public void release(UUID transactionId) {
        unitOfWork.inTransaction(() -> {
            Instant now = clock.instant();
            reservations.release(transactionId, now).ifPresent(released ->
                    stock.findByIdForUpdate(released.productId()).ifPresent(product -> {
                        product.release(released.quantity(), now);
                        stock.updateStock(product);
                    }));
            return null;
        });
    }

    private static Reservation retry(StockReservation earlier, UUID productId, Quantity quantity) {
        if (!earlier.isFor(productId, quantity)) {
            throw new ReservationMismatchException(earlier.transactionId());
        }
        return new Reservation(earlier, false);
    }
}
