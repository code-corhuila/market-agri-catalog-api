package co.edu.corhuila.marketagri.catalog.domain.model;

import java.util.UUID;

/** No such product, or a deleted one (INV-007): {@code 404 PRODUCT_NOT_FOUND}. */
public final class ProductNotFoundException extends DomainException {

    public ProductNotFoundException(UUID id) {
        super("PRODUCT_NOT_FOUND", "product not found: " + id);
    }
}
