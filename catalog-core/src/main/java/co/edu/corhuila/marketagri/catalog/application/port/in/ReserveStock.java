package co.edu.corhuila.marketagri.catalog.application.port.in;

import co.edu.corhuila.marketagri.catalog.domain.model.StockReservation;
import java.math.BigDecimal;
import java.util.UUID;

/** The checkout saga holds stock before the payment (I-03, HU-TXN-001). Idempotent on {@code transactionId}. */
public interface ReserveStock {

    /**
     * Subtracts the quantity and stores the reservation, or answers the stored one on a retry.
     * Errors in the order of the contract: {@code QUANTITY_NOT_POSITIVE}, {@code PRODUCT_NOT_FOUND},
     * {@code RESERVATION_MISMATCH}, {@code PRODUCT_NOT_AVAILABLE}, {@code INSUFFICIENT_STOCK}.
     */
    Reservation reserve(UUID productId, UUID transactionId, BigDecimal quantity);

    /** {@code created} is false on a retry: the HTTP adapter answers 200 instead of 201. */
    record Reservation(StockReservation reservation, boolean created) {
    }
}
