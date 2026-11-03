package co.edu.corhuila.marketagri.catalog.application.port.in;

import co.edu.corhuila.marketagri.catalog.domain.model.Product;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductCategory;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductUnit;
import java.math.BigDecimal;
import java.util.UUID;

/** A producer publishes a product (E-13, HU-CAT-001). Idempotent on the producer and the key (D-C35). */
public interface PublishProduct {

    /**
     * Creates the product, or answers the one created earlier with the same key. Errors in the
     * order of the contract: {@code PRICE_NOT_POSITIVE}, then {@code QUANTITY_NEGATIVE}; the
     * replay check comes after them.
     */
    Publication publish(Command command);

    /** {@code producerId} and {@code producerName} come from the token, never from the body (D-C31). */
    record Command(UUID producerId, String producerName, String idempotencyKey, String name,
                   ProductCategory category, ProductUnit unit, BigDecimal quantity, long priceCents,
                   String municipality) {
    }

    /** {@code created} is false on a replay: the HTTP adapter answers 200 instead of 201. */
    record Publication(Product product, boolean created) {
    }
}
