package co.edu.corhuila.marketagri.catalog.domain.model;

/**
 * The filters of E-10 (HU-CAT-006, FR-015): each one is optional ({@code null} = not filtered) and
 * they combine with AND. The municipality is compared through its {@link MunicipalityKey} (D-16), so
 * {@code garzon} finds {@code Garzón}; the status through the quantity, the way {@link Product}
 * derives it (INV-009).
 */
public record ProductFilter(ProductCategory category, MunicipalityKey municipality, ProductStatus status) {

    private static final ProductFilter NONE = new ProductFilter(null, null, null);

    /** Every product that is not deleted. */
    public static ProductFilter none() {
        return NONE;
    }

    /** What the repository has to agree with: the in-memory one uses it and the JDBC one is tested against it. */
    public boolean matches(Product product) {
        return (category == null || category == product.category())
                && (municipality == null || municipality.equals(product.municipalityKey()))
                && (status == null || status == product.status());
    }
}
