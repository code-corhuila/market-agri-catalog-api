package co.edu.corhuila.marketagri.catalog.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.marketagri.catalog.application.port.out.UnitOfWork;
import co.edu.corhuila.marketagri.catalog.domain.model.Money;
import co.edu.corhuila.marketagri.catalog.domain.model.Product;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductCategory;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductUnit;
import co.edu.corhuila.marketagri.catalog.domain.model.Quantity;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * E-16 through the real servlet container: MockMvc skips the multipart limits of application.yml,
 * so the 5 MB ceiling of D-C30 ({@code 413 PAYLOAD_TOO_LARGE}) is checked here, over HTTP.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class CatalogPhotoUploadTest extends CatalogSpringTest {

    private static final UUID ANA = UUID.fromString("0b6f2c7e-4a51-4c1e-9d0e-2f1c3a7b9e10");
    private static final int FIVE_MB = 5 * 1024 * 1024;
    private static final String BOUNDARY = "catalog-photo-boundary";
    private static final Path MEDIA = temporaryFolder();

    @DynamicPropertySource
    static void mediaFolder(DynamicPropertyRegistry registry) {
        registry.add("media.dir", MEDIA::toString);
    }

    private static Path temporaryFolder() {
        try {
            return Files.createTempDirectory("catalog-media-");
        } catch (IOException cannotCreate) {
            throw new UncheckedIOException(cannotCreate);
        }
    }

    @TestConfiguration
    static class InMemory {
        @Bean
        @Primary
        InMemoryProductRepository inMemoryProducts() {
            return new InMemoryProductRepository();
        }

        @Bean
        @Primary
        UnitOfWork directUnitOfWork() {
            return new UnitOfWork() {
                @Override
                public <T> T inTransaction(Supplier<T> work) {
                    return work.get();
                }
            };
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private InMemoryProductRepository products;

    private final HttpClient client = HttpClient.newHttpClient();
    private Product cafe;

    @BeforeEach
    void oneProductOfAna() {
        products.clear();
        cafe = Product.create(UUID.randomUUID(), ANA, "Ana Ruiz", "Café pergamino seco", ProductCategory.CAFE,
                ProductUnit.ARROBA, Quantity.of("40"), Money.ofCents(8_500_000), "Garzón",
                Instant.parse("2026-11-01T15:00:00Z"));
        products.add(cafe);
    }

    /** A PNG signature followed by {@code size - 8} zeros: an image as far as E-16 can tell. */
    private static byte[] png(int size) {
        byte[] content = new byte[size];
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(signature, 0, content, 0, signature.length);
        return content;
    }

    private HttpResponse<String> upload(byte[] content) throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"foto.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        body.write(content);
        body.write(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.US_ASCII));
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/v1/catalog/products/" + cafe.id() + "/photo"))
                .header("Authorization", "Bearer " + TestTokens.user("PRODUCER").claim("sub", ANA.toString()).signed())
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void aPhotoOfFiveMegabytesIsAccepted() throws Exception {
        HttpResponse<String> response = upload(png(FIVE_MB));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"photoUrl\":\"/media/");
    }

    @Test
    void aPhotoOverFiveMegabytesIsPayloadTooLarge() throws Exception {
        HttpResponse<String> response = upload(png(FIVE_MB + 1));

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.body()).contains("\"error\":\"PAYLOAD_TOO_LARGE\"")
                .contains("\"message\":\"La imagen supera el máximo de 5 MB.\"");
        assertThat(cafe.photoUrl()).isNull();
    }
}
