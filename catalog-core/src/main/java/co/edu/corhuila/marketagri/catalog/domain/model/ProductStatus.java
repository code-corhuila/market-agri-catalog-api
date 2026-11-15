package co.edu.corhuila.marketagri.catalog.domain.model;

/** Status of a product (D-C5): never stored on its own, always derived from the quantity (INV-009). */
public enum ProductStatus {
    ACTIVE,
    OUT_OF_STOCK;

    /** INV-009: {@code quantity > 0} is {@link #ACTIVE}, {@code quantity = 0} is {@link #OUT_OF_STOCK}. */
    static ProductStatus of(Quantity quantity) {
        return quantity.isZero() ? OUT_OF_STOCK : ACTIVE;
    }
}
