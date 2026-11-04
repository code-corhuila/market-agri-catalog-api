package co.edu.corhuila.marketagri.catalog.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.marketagri.catalog.application.port.out.ProductEdits;
import co.edu.corhuila.marketagri.catalog.application.port.out.ProductRepository;
import co.edu.corhuila.marketagri.catalog.domain.model.Money;
import co.edu.corhuila.marketagri.catalog.domain.model.MunicipalityKey;
import co.edu.corhuila.marketagri.catalog.domain.model.Page;
import co.edu.corhuila.marketagri.catalog.domain.model.PageRequest;
import co.edu.corhuila.marketagri.catalog.domain.model.Product;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductCategory;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductFilter;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductStatus;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductUnit;
import co.edu.corhuila.marketagri.catalog.domain.model.Quantity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * The JDBC repository against the schema of catalog-db (Anexo C, "Integración": round trip, page,
 * the filters of E-10, my products of E-12 and the edit of E-14). catalog_app has no DELETE (D-14), so every test runs in a transaction that is rolled back.
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class CatalogProductsIT extends CatalogSpringTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TEST_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> "");
        registry.add("spring.datasource.password", () -> "");
    }

    private static final UUID PRODUCER = UUID.fromString("a1b2c3d4-4444-0000-0000-000000000001");

    @Autowired
    private ProductRepository repository;

    @Autowired
    private ProductEdits edits;

    @Autowired
    private JdbcClient jdbc;

    private UUID insert(String quantity, String createdAt, String photoUrl, boolean deleted) {
        return insert("CAFE", "Garzón", quantity, createdAt, photoUrl, deleted);
    }

    private UUID insert(String category, String municipality, String quantity, String createdAt) {
        return insert(category, municipality, quantity, createdAt, null, false);
    }

    private UUID insert(String category, String municipality, String quantity, String createdAt,
                        String photoUrl, boolean deleted) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO catalog.product (id, producer_id, producer_name, name, category, unit, quantity,
                    price_cents, municipality, municipality_key, photo_url, status, created_at, updated_at, deleted_at)
                VALUES (:id, :producer, 'Ana Ruiz', 'Café pergamino seco', :category, 'ARROBA', :quantity,
                    680000, :municipality, :municipalityKey, :photo, :status, CAST(:created AS timestamptz),
                    CAST(:created AS timestamptz), CASE WHEN :deleted THEN now() END)
                """)
                .param("id", id).param("producer", PRODUCER).param("quantity", new java.math.BigDecimal(quantity))
                .param("photo", photoUrl).param("status", "0".equals(quantity) ? "OUT_OF_STOCK" : "ACTIVE")
                .param("created", createdAt).param("deleted", deleted)
                .param("category", category).param("municipality", municipality)
                .param("municipalityKey", MunicipalityKey.of(municipality).value())
                .update();
        return id;
    }

    /** CatalogReservationsIT commits its products and leaves them deleted: deleted rows do not count. */
    @BeforeEach
    void startFromAnEmptyCatalogInThisTransaction() {
        assertThat(jdbc.sql("SELECT count(*) FROM catalog.product WHERE deleted_at IS NULL")
                .query(Long.class).single()).isZero();
    }

    @Test
    void roundTripKeepsEveryColumn() {
        UUID id = insert("12.5", "2026-10-02T10:00:00Z", "/media/cafe.jpg", false);

        Product product = repository.findById(id).orElseThrow();

        assertThat(product.producerId()).isEqualTo(PRODUCER);
        assertThat(product.producerName()).isEqualTo("Ana Ruiz");
        assertThat(product.category()).isEqualTo(ProductCategory.CAFE);
        assertThat(product.unit()).isEqualTo(ProductUnit.ARROBA);
        assertThat(product.quantity()).isEqualTo(Quantity.of("12.5"));
        assertThat(product.price()).isEqualTo(Money.ofCents(680_000));
        assertThat(product.municipality()).isEqualTo("Garzón");
        assertThat(product.photoUrl()).isEqualTo("/media/cafe.jpg");
        assertThat(product.status()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(product.createdAt()).isEqualTo(java.time.Instant.parse("2026-10-02T10:00:00Z"));
    }

    @Test
    void deletedProductIsNotFound() {
        UUID id = insert("3", "2026-10-02T10:00:00Z", null, true);

        assertThat(repository.findById(id)).isEmpty();
    }

    @Test
    void pageIsNewestFirstAndSkipsDeleted() {
        UUID older = insert("1", "2026-10-01T10:00:00Z", null, false);
        UUID newer = insert("0", "2026-10-03T10:00:00Z", null, false);
        insert("5", "2026-10-04T10:00:00Z", null, true);

        Page<Product> page = repository.findPage(ProductFilter.none(), new PageRequest(1, 20));

        assertThat(page.items()).extracting(Product::id).containsExactly(newer, older);
        assertThat(page.items().get(0).status()).isEqualTo(ProductStatus.OUT_OF_STOCK);
        assertThat(page.total()).isEqualTo(2L);
    }

    @Test
    void secondPageUsesLimitAndOffset() {
        insert("1", "2026-10-01T10:00:00Z", null, false);
        UUID middle = insert("1", "2026-10-02T10:00:00Z", null, false);
        insert("1", "2026-10-03T10:00:00Z", null, false);

        Page<Product> page = repository.findPage(ProductFilter.none(), new PageRequest(2, 1));

        assertThat(page.items()).extracting(Product::id).containsExactly(middle);
        assertThat(page.total()).isEqualTo(3L);
        assertThat(page.totalPages()).isEqualTo(3);
    }

    /** The SQL of each filter agrees with {@link ProductFilter#matches}: same products, same total. */
    @Test
    void eachFilterAloneAndCombinedWithAnd() {
        UUID cafeGarzon = insert("CAFE", "Garzón", "4", "2026-10-01T10:00:00Z");
        UUID cafeGarzonSoldOut = insert("CAFE", "Garzón", "0", "2026-10-02T10:00:00Z");
        UUID cacaoNeiva = insert("CACAO", "Neiva", "7", "2026-10-03T10:00:00Z");
        UUID cafeNeiva = insert("CAFE", "Neiva", "2", "2026-10-04T10:00:00Z");
        insert("CAFE", "Garzón", "5", "2026-10-05T10:00:00Z", null, true);

        assertThat(ids(new ProductFilter(ProductCategory.CAFE, null, null)))
                .containsExactly(cafeNeiva, cafeGarzonSoldOut, cafeGarzon);
        assertThat(ids(new ProductFilter(null, MunicipalityKey.of("GARZON"), null)))
                .containsExactly(cafeGarzonSoldOut, cafeGarzon);
        assertThat(ids(new ProductFilter(null, null, ProductStatus.OUT_OF_STOCK)))
                .containsExactly(cafeGarzonSoldOut);
        assertThat(ids(new ProductFilter(null, null, ProductStatus.ACTIVE)))
                .containsExactly(cafeNeiva, cacaoNeiva, cafeGarzon);
        assertThat(ids(new ProductFilter(ProductCategory.CAFE, MunicipalityKey.of("neiva"), ProductStatus.ACTIVE)))
                .containsExactly(cafeNeiva);
        assertThat(ids(new ProductFilter(ProductCategory.LACTEOS, null, null))).isEmpty();
    }

    @Test
    void totalCountsOnlyTheFilteredProducts() {
        insert("CAFE", "Garzón", "1", "2026-10-01T10:00:00Z");
        UUID newest = insert("CAFE", "Garzón", "1", "2026-10-03T10:00:00Z");
        insert("CACAO", "Garzón", "1", "2026-10-02T10:00:00Z");

        Page<Product> page = repository.findPage(new ProductFilter(ProductCategory.CAFE, null, null),
                new PageRequest(1, 1));

        assertThat(page.items()).extracting(Product::id).containsExactly(newest);
        assertThat(page.total()).isEqualTo(2L);
    }

    private List<UUID> ids(ProductFilter filter) {
        Page<Product> page = repository.findPage(filter, new PageRequest(1, 20));
        assertThat(page.total()).isEqualTo(page.items().size());
        return page.items().stream().map(Product::id).toList();
    }

    @Test
    void mineIsOnlyThatProducersProducts() {
        UUID mine = insert("CAFE", "Garzón", "1", "2026-10-01T10:00:00Z");
        UUID mineSoldOut = insert("CAFE", "Garzón", "0", "2026-10-02T10:00:00Z");
        UUID someoneElses = insert("CAFE", "Garzón", "1", "2026-10-03T10:00:00Z");
        UUID other = UUID.fromString("a1b2c3d4-4444-0000-0000-000000000002");
        jdbc.sql("UPDATE catalog.product SET producer_id = :other WHERE id = :id")
                .param("other", other).param("id", someoneElses).update();

        assertThat(ids(ProductFilter.mine(PRODUCER, null))).containsExactly(mineSoldOut, mine);
        assertThat(ids(ProductFilter.mine(PRODUCER, ProductStatus.ACTIVE))).containsExactly(mine);
        assertThat(ids(ProductFilter.mine(other, null))).containsExactly(someoneElses);
    }

    /** E-14: every edited column, the municipality key and the derived status reach the row. */
    @Test
    void editStoresTheDetailsTheKeyAndTheDerivedStatus() {
        UUID id = insert("12.5", "2026-10-02T10:00:00Z", "/media/cafe.jpg", false);
        Instant edited = Instant.parse("2026-11-04T15:00:00Z");
        Product product = edits.findByIdForUpdate(id).orElseThrow();

        product.edit("Cacao fino", ProductCategory.CACAO, ProductUnit.KILOGRAMO, Quantity.of("0"),
                Money.ofCents(1_200_000), "San Agustín", edited);
        edits.updateDetails(product);

        Product stored = repository.findById(id).orElseThrow();
        assertThat(stored.name()).isEqualTo("Cacao fino");
        assertThat(stored.category()).isEqualTo(ProductCategory.CACAO);
        assertThat(stored.unit()).isEqualTo(ProductUnit.KILOGRAMO);
        assertThat(stored.price()).isEqualTo(Money.ofCents(1_200_000));
        assertThat(stored.municipality()).isEqualTo("San Agustín");
        assertThat(stored.photoUrl()).isEqualTo("/media/cafe.jpg");
        assertThat(stored.updatedAt()).isEqualTo(edited);
        assertThat(jdbc.sql("SELECT status || ' ' || municipality_key FROM catalog.product WHERE id = :id")
                .param("id", id).query(String.class).single()).isEqualTo("OUT_OF_STOCK san agustin");
        assertThat(ids(new ProductFilter(null, MunicipalityKey.of("san agustin"), null))).containsExactly(id);
    }

    @Test
    void deletedProductCannotBeEdited() {
        UUID id = insert("3", "2026-10-02T10:00:00Z", null, true);

        assertThat(edits.findByIdForUpdate(id)).isEmpty();
    }
}
