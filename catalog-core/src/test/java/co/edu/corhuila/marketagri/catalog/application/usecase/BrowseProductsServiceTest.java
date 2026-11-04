package co.edu.corhuila.marketagri.catalog.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.marketagri.catalog.application.port.out.ProductRepository;
import co.edu.corhuila.marketagri.catalog.domain.model.Money;
import co.edu.corhuila.marketagri.catalog.domain.model.Page;
import co.edu.corhuila.marketagri.catalog.domain.model.PageRequest;
import co.edu.corhuila.marketagri.catalog.domain.model.Product;
import co.edu.corhuila.marketagri.catalog.domain.model.MunicipalityKey;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductCategory;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductFilter;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductNotFoundException;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductUnit;
import co.edu.corhuila.marketagri.catalog.domain.model.Quantity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** E-10 and E-11 in the core, with a fake of the repository port: no server, no database. */
class BrowseProductsServiceTest {

    private static final Product CAFE = Product.create(UUID.randomUUID(), UUID.randomUUID(), "Ana Ruiz",
            "Café pergamino seco", ProductCategory.CAFE, ProductUnit.ARROBA, Quantity.of("40"),
            Money.ofCents(8_500_000), "Garzón", Instant.parse("2026-11-01T15:00:00Z"));

    /** Remembers the filter and the page it was asked for and answers one product. */
    private static final class FakeRepository implements ProductRepository {
        ProductFilter askedFilter;
        PageRequest asked;

        @Override
        public Page<Product> findPage(ProductFilter filter, PageRequest request) {
            askedFilter = filter;
            asked = request;
            return new Page<>(List.of(CAFE), request, 41);
        }

        @Override
        public Optional<Product> findById(UUID id) {
            return CAFE.id().equals(id) ? Optional.of(CAFE) : Optional.empty();
        }
    }

    private final FakeRepository repository = new FakeRepository();
    private final BrowseProductsService browse = new BrowseProductsService(repository);

    @Test
    void listAsksTheRepositoryForTheRequestedFilterAndPage() {
        ProductFilter garzon = new ProductFilter(ProductCategory.CAFE, MunicipalityKey.of("Garzón"), null);

        Page<Product> page = browse.list(garzon, new PageRequest(2, 20));

        assertThat(repository.askedFilter).isEqualTo(garzon);
        assertThat(repository.asked).isEqualTo(new PageRequest(2, 20));
        assertThat(page.items()).containsExactly(CAFE);
        assertThat(page.total()).isEqualTo(41L);
    }

    @Test
    void getReturnsTheProduct() {
        assertThat(browse.get(CAFE.id())).isSameAs(CAFE);
    }

    @Test
    void getOfAnUnknownProductIsProductNotFound() {
        assertThatThrownBy(() -> browse.get(UUID.randomUUID()))
                .isInstanceOfSatisfying(ProductNotFoundException.class,
                        refused -> assertThat(refused.code()).isEqualTo("PRODUCT_NOT_FOUND"));
    }
}
