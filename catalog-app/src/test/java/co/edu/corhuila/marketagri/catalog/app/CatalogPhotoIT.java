package co.edu.corhuila.marketagri.catalog.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.marketagri.catalog.application.port.in.ManageProductPhoto;
import co.edu.corhuila.marketagri.catalog.application.port.in.PublishProduct;
import co.edu.corhuila.marketagri.catalog.application.port.in.PublishProduct.Command;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductCategory;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductUnit;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * E-16 and E-17 against the schema of catalog-db, as catalog_app: the key is written in the
 * {@code photo_url} column and satisfies {@code chk_product_photo_url}; removing it leaves
 * {@code NULL}, never {@code ''} (D-C8). Products are left deleted.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class CatalogPhotoIT extends CatalogSpringTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
    private static final Path MEDIA = temporaryFolder();

    @DynamicPropertySource
    static void databaseAndMedia(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TEST_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> "");
        registry.add("spring.datasource.password", () -> "");
        registry.add("media.dir", MEDIA::toString);
    }

    private static Path temporaryFolder() {
        try {
            return Files.createTempDirectory("catalog-media-");
        } catch (IOException cannotCreate) {
            throw new UncheckedIOException(cannotCreate);
        }
    }

    @Autowired
    private PublishProduct publish;

    @Autowired
    private ManageProductPhoto photos;

    @Autowired
    private JdbcClient jdbc;

    private final UUID producer = UUID.randomUUID();

    @AfterEach
    void leaveThemDeleted() {
        jdbc.sql("UPDATE catalog.product SET deleted_at = now() WHERE producer_id = :p").param("p", producer).update();
    }

    private UUID published() {
        return publish.publish(new Command(producer, "Ana Ruiz", "photo-" + UUID.randomUUID(), "Café pergamino seco",
                ProductCategory.CAFE, ProductUnit.ARROBA, new BigDecimal("12.5"), 8_500_000, "Garzón"))
                .product().id();
    }

    private String storedUrl(UUID id) {
        return (String) jdbc.sql("SELECT photo_url FROM catalog.product WHERE id = :id").param("id", id)
                .query().singleRow().get("photo_url");
    }

    @Test
    void thePhotoKeyIsStoredAndTheBinaryWritten() {
        UUID id = published();

        String url = photos.setPhoto(id, producer, PNG).photoUrl();

        assertThat(storedUrl(id)).isEqualTo(url).matches("^/media/[0-9a-f]{32}\\.png$");
        assertThat(MEDIA.resolve(url.substring("/media/".length()))).exists();
    }

    @Test
    void removingThePhotoLeavesNullAndDeletesTheBinary() {
        UUID id = published();
        String url = photos.setPhoto(id, producer, PNG).photoUrl();

        photos.removePhoto(id, producer);

        assertThat(storedUrl(id)).isNull();
        assertThat(MEDIA.resolve(url.substring("/media/".length()))).doesNotExist();
    }
}
