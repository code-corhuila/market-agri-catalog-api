package co.edu.corhuila.marketagri.catalog.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Stock held for a pending payment (I-03), identified by the {@code transactionId} of the saga.
 * It freezes what the transaction needs — name, unit and price at reservation time — so a retry
 * answers the same body even if the product changed afterwards (api-contract §4.2).
 */
public record StockReservation(UUID transactionId, UUID productId, UUID producerId, String productName,
                               ProductUnit unit, Quantity quantity, Money unitPrice,
                               Quantity remainingQuantity, Instant createdAt) {

    public StockReservation {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(remainingQuantity, "remainingQuantity");
    }

    /** The snapshot of a product that has just been reserved: what is left is its quantity now. */
    public static StockReservation of(UUID transactionId, Product reserved, Quantity quantity, Instant now) {
        return new StockReservation(transactionId, reserved.id(), reserved.producerId(), reserved.name(),
                reserved.unit(), quantity, reserved.price(), reserved.quantity(), now);
    }

    /** A retry is the same request only with the same product and quantity. */
    public boolean isFor(UUID otherProductId, Quantity otherQuantity) {
        return productId.equals(otherProductId) && quantity.equals(otherQuantity);
    }
}
