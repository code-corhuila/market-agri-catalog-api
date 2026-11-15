package co.edu.corhuila.marketagri.catalog.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** HU-CAT-006: what each filter of E-10 lets through, alone and combined with AND. */
class ProductFilterTest {

    private static Product product(ProductCategory category, String municipality, String quantity) {
        return Product.create(UUID.randomUUID(), UUID.randomUUID(), "Ana Ruiz", "Producto", category,
                ProductUnit.KILOGRAMO, Quantity.of(quantity), Money.ofCents(100_000), municipality,
                Instant.parse("2026-11-01T15:00:00Z"));
    }

    private static final Product CAFE_GARZON = product(ProductCategory.CAFE, "Garzón", "40");
    private static final Product CAFE_GARZON_SOLD_OUT = product(ProductCategory.CAFE, "Garzón", "0");
    private static final Product CACAO_NEIVA = product(ProductCategory.CACAO, "Neiva", "3");

    @Test
    void noneLetsEveryProductThrough() {
        assertThat(ProductFilter.none().matches(CAFE_GARZON)).isTrue();
        assertThat(ProductFilter.none().matches(CACAO_NEIVA)).isTrue();
    }

    @Test
    void categoryIsExact() {
        ProductFilter cafe = new ProductFilter(ProductCategory.CAFE, null, null);

        assertThat(cafe.matches(CAFE_GARZON)).isTrue();
        assertThat(cafe.matches(CACAO_NEIVA)).isFalse();
    }

    @Test
    void municipalityIgnoresCaseAndAccents() {
        ProductFilter garzon = new ProductFilter(null, MunicipalityKey.of("GARZON"), null);

        assertThat(garzon.matches(CAFE_GARZON)).isTrue();
        assertThat(garzon.matches(CACAO_NEIVA)).isFalse();
    }

    @Test
    void statusFollowsTheQuantity() {
        ProductFilter soldOut = new ProductFilter(null, null, ProductStatus.OUT_OF_STOCK);

        assertThat(soldOut.matches(CAFE_GARZON_SOLD_OUT)).isTrue();
        assertThat(soldOut.matches(CAFE_GARZON)).isFalse();
    }

    @Test
    void filtersCombineWithAnd() {
        ProductFilter cafeActiveInNeiva =
                new ProductFilter(ProductCategory.CAFE, MunicipalityKey.of("Neiva"), ProductStatus.ACTIVE);

        assertThat(cafeActiveInNeiva.matches(CAFE_GARZON)).isFalse();
        assertThat(cafeActiveInNeiva.matches(CACAO_NEIVA)).isFalse();
        assertThat(new ProductFilter(ProductCategory.CAFE, MunicipalityKey.of("garzón"), ProductStatus.ACTIVE)
                .matches(CAFE_GARZON)).isTrue();
    }
}
