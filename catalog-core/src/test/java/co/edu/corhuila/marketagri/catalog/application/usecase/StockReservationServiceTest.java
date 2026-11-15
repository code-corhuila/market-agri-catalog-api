package co.edu.corhuila.marketagri.catalog.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.marketagri.catalog.application.port.in.ReserveStock.Reservation;
import co.edu.corhuila.marketagri.catalog.application.port.out.ProductStock;
import co.edu.corhuila.marketagri.catalog.application.port.out.ReservationRepository;
import co.edu.corhuila.marketagri.catalog.application.port.out.UnitOfWork;
import co.edu.corhuila.marketagri.catalog.domain.model.InsufficientStockException;
import co.edu.corhuila.marketagri.catalog.domain.model.Money;
import co.edu.corhuila.marketagri.catalog.domain.model.Product;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductCategory;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductNotFoundException;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductStatus;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductUnit;
import co.edu.corhuila.marketagri.catalog.domain.model.Quantity;
import co.edu.corhuila.marketagri.catalog.domain.model.QuantityNotPositiveException;
import co.edu.corhuila.marketagri.catalog.domain.model.ReservationMismatchException;
import co.edu.corhuila.marketagri.catalog.domain.model.StockReservation;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** I-03 and I-04 in the core, with fakes of the ports: the order of the checks and "once". */
class StockReservationServiceTest {

    private static final Instant CREATED = Instant.parse("2026-11-01T15:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
    private static final UUID TRANSACTION = UUID.fromString("7f000000-0000-0000-0000-000000000001");

    /** Fails any repository call made outside a transaction. */
    private static final class FakeUnitOfWork implements UnitOfWork {
        boolean inside;

        @Override
        public <T> T inTransaction(Supplier<T> work) {
            inside = true;
            try {
                return work.get();
            } finally {
                inside = false;
            }
        }

        void requireInside() {
            assertThat(inside).as("called inside the unit of work").isTrue();
        }
    }

    private final class FakeProducts implements ProductStock {
        final Map<UUID, Product> stored = new HashMap<>();
        int updates;

        @Override
        public Optional<Product> findByIdForUpdate(UUID id) {
            unitOfWork.requireInside();
            return Optional.ofNullable(stored.get(id));
        }

        @Override
        public void updateStock(Product product) {
            unitOfWork.requireInside();
            updates++;
        }
    }

    private final class FakeReservations implements ReservationRepository {
        final Map<UUID, StockReservation> stored = new HashMap<>();
        final Map<UUID, Instant> released = new HashMap<>();
        boolean raceOnAdd;

        @Override
        public Optional<StockReservation> findByTransactionId(UUID transactionId) {
            unitOfWork.requireInside();
            return Optional.ofNullable(stored.get(transactionId));
        }

        @Override
        public boolean add(StockReservation reservation) {
            unitOfWork.requireInside();
            if (raceOnAdd) {
                return false;
            }
            return stored.putIfAbsent(reservation.transactionId(), reservation) == null;
        }

        @Override
        public Optional<StockReservation> release(UUID transactionId, Instant now) {
            unitOfWork.requireInside();
            if (!stored.containsKey(transactionId) || released.containsKey(transactionId)) {
                return Optional.empty();
            }
            released.put(transactionId, now);
            return Optional.of(stored.get(transactionId));
        }
    }

    private final FakeUnitOfWork unitOfWork = new FakeUnitOfWork();
    private final FakeProducts products = new FakeProducts();
    private final FakeReservations reservations = new FakeReservations();
    private final StockReservationService service = new StockReservationService(products, reservations,
            unitOfWork, Clock.fixed(NOW, ZoneOffset.UTC));

    private Product stored(String quantity) {
        Product product = Product.create(UUID.randomUUID(), UUID.randomUUID(), "Ana Ruiz", "Café pergamino seco",
                ProductCategory.CAFE, ProductUnit.ARROBA, Quantity.of(quantity), Money.ofCents(8_500_000),
                "Garzón", CREATED);
        products.stored.put(product.id(), product);
        return product;
    }

    private static BigDecimal amount(String value) {
        return new BigDecimal(value);
    }

    @Test
    void reservationSubtractsAndFreezesTheSnapshot() {
        Product cafe = stored("10");

        Reservation result = service.reserve(cafe.id(), TRANSACTION, amount("4"));

        assertThat(result.created()).isTrue();
        StockReservation reservation = result.reservation();
        assertThat(reservation.transactionId()).isEqualTo(TRANSACTION);
        assertThat(reservation.productId()).isEqualTo(cafe.id());
        assertThat(reservation.producerId()).isEqualTo(cafe.producerId());
        assertThat(reservation.productName()).isEqualTo("Café pergamino seco");
        assertThat(reservation.unit()).isEqualTo(ProductUnit.ARROBA);
        assertThat(reservation.quantity()).isEqualTo(Quantity.of("4"));
        assertThat(reservation.unitPrice()).isEqualTo(Money.ofCents(8_500_000));
        assertThat(reservation.remainingQuantity()).isEqualTo(Quantity.of("6"));
        assertThat(reservation.createdAt()).isEqualTo(NOW);
        assertThat(cafe.quantity()).isEqualTo(Quantity.of("6"));
        assertThat(products.updates).isEqualTo(1);
        assertThat(reservations.stored).containsKey(TRANSACTION);
    }

    @Test
    void retryWithTheSameDataAnswersTheStoredReservationAndSubtractsNothing() {
        Product cafe = stored("10");
        StockReservation first = service.reserve(cafe.id(), TRANSACTION, amount("4")).reservation();

        Reservation retry = service.reserve(cafe.id(), TRANSACTION, amount("4.00"));

        assertThat(retry.created()).isFalse();
        assertThat(retry.reservation()).isEqualTo(first);
        assertThat(cafe.quantity()).isEqualTo(Quantity.of("6"));
        assertThat(products.updates).isEqualTo(1);
    }

    @Test
    void retryAfterTheLastUnitsIsStillTheSameReservation() {
        Product cafe = stored("4");
        service.reserve(cafe.id(), TRANSACTION, amount("4"));

        assertThat(service.reserve(cafe.id(), TRANSACTION, amount("4")).created()).isFalse();
        assertThat(cafe.status()).isEqualTo(ProductStatus.OUT_OF_STOCK);
    }

    @Test
    void sameTransactionWithAnotherQuantityIsAMismatch() {
        Product cafe = stored("10");
        service.reserve(cafe.id(), TRANSACTION, amount("4"));

        assertThatThrownBy(() -> service.reserve(cafe.id(), TRANSACTION, amount("5")))
                .isInstanceOf(ReservationMismatchException.class);
        assertThat(cafe.quantity()).isEqualTo(Quantity.of("6"));
    }

    @Test
    void sameTransactionOnAnotherProductIsAMismatch() {
        Product cafe = stored("10");
        Product other = stored("10");
        service.reserve(cafe.id(), TRANSACTION, amount("4"));

        assertThatThrownBy(() -> service.reserve(other.id(), TRANSACTION, amount("4")))
                .isInstanceOf(ReservationMismatchException.class);
        assertThat(other.quantity()).isEqualTo(Quantity.of("10"));
    }

    @Test
    void aRaceOnTheSameTransactionIsAMismatch() {
        Product cafe = stored("10");
        reservations.raceOnAdd = true;

        assertThatThrownBy(() -> service.reserve(cafe.id(), TRANSACTION, amount("4")))
                .isInstanceOf(ReservationMismatchException.class);
    }

    @Test
    void notPositiveIsCheckedBeforeTheProduct() {
        assertThatThrownBy(() -> service.reserve(UUID.randomUUID(), TRANSACTION, amount("0")))
                .isInstanceOf(QuantityNotPositiveException.class);
        assertThatThrownBy(() -> service.reserve(UUID.randomUUID(), TRANSACTION, amount("-2")))
                .isInstanceOf(QuantityNotPositiveException.class);
    }

    @Test
    void unknownProductIsNotFoundBeforeTheMismatch() {
        Product cafe = stored("10");
        service.reserve(cafe.id(), TRANSACTION, amount("4"));

        assertThatThrownBy(() -> service.reserve(UUID.randomUUID(), TRANSACTION, amount("4")))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void refusedReservationStoresNothing() {
        Product cafe = stored("3");

        assertThatThrownBy(() -> service.reserve(cafe.id(), TRANSACTION, amount("5")))
                .isInstanceOf(InsufficientStockException.class);
        assertThat(products.updates).isZero();
        assertThat(reservations.stored).isEmpty();
    }

    @Test
    void releaseGivesTheStockBackOnce() {
        Product cafe = stored("4");
        service.reserve(cafe.id(), TRANSACTION, amount("4"));

        service.release(TRANSACTION);
        service.release(TRANSACTION);

        assertThat(cafe.quantity()).isEqualTo(Quantity.of("4"));
        assertThat(cafe.status()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(cafe.updatedAt()).isEqualTo(NOW);
        assertThat(reservations.released).containsEntry(TRANSACTION, NOW);
    }

    @Test
    void releaseOfAnUnknownTransactionChangesNothing() {
        service.release(TRANSACTION);

        assertThat(products.updates).isZero();
    }

    @Test
    void releaseOfADeletedProductOnlyMarksTheReservation() {
        Product cafe = stored("10");
        service.reserve(cafe.id(), TRANSACTION, amount("4"));
        products.stored.remove(cafe.id());

        service.release(TRANSACTION);

        assertThat(reservations.released).containsKey(TRANSACTION);
        assertThat(products.updates).isEqualTo(1);
    }
}
