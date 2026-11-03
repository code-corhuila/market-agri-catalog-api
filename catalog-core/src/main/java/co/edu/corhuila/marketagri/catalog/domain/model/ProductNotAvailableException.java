package co.edu.corhuila.marketagri.catalog.domain.model;

import java.util.UUID;

/** A reservation on a product {@code OUT_OF_STOCK} (I-03, {@code 409 PRODUCT_NOT_AVAILABLE}). */
public final class ProductNotAvailableException extends DomainException {

    public ProductNotAvailableException(UUID id) {
        super("PRODUCT_NOT_AVAILABLE", "product out of stock: " + id);
    }
}
