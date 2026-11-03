package co.edu.corhuila.marketagri.catalog.adapter.in.http;

import co.edu.corhuila.marketagri.catalog.domain.model.StockReservation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code ReservationView} of I-03 (api-contract §4.2). {@code unitPriceCents} is the price at
 * reservation time: transactions computes {@code amountCents} from it (ADR-029).
 */
public record ReservationView(UUID transactionId, UUID productId, String productName, UUID producerId,
                              BigDecimal quantity, String unit, long unitPriceCents, String currency,
                              BigDecimal remainingQuantity, Instant createdAt) {

    private static final String CURRENCY = "COP";

    static ReservationView of(StockReservation reservation) {
        return new ReservationView(reservation.transactionId(), reservation.productId(),
                reservation.productName(), reservation.producerId(), reservation.quantity().value(),
                reservation.unit().name(), reservation.unitPrice().cents(), CURRENCY,
                reservation.remainingQuantity().value(), reservation.createdAt());
    }
}
