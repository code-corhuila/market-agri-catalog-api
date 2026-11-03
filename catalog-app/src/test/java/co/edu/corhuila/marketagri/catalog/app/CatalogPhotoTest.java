package co.edu.corhuila.marketagri.catalog.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.marketagri.catalog.application.port.out.UnitOfWork;
import co.edu.corhuila.marketagri.catalog.domain.model.Money;
import co.edu.corhuila.marketagri.catalog.domain.model.Product;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductCategory;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductUnit;
import co.edu.corhuila.marketagri.catalog.domain.model.Quantity;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * E-16, E-17 and E-62 over HTTP (HU-CAT-001 AC5–AC6, D-C8, D-C30), with the product ports in memory
 * and the photos in a temporary folder: the cards of api-contract §4.2, the order of their errors,
 * and a media route anyone can read, cached for a day.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CatalogPhotoTest extends CatalogSpringTest {

    private static final String PHOTO = "/api/v1/catalog/products/{id}/photo";
    private static final UUID ANA = UUID.fromString("0b6f2c7e-4a51-4c1e-9d0e-2f1c3a7b9e10");
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 4, 5};
    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-', '1', '.', '7'};
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

    @Autowired
    private MockMvc http;

    @Autowired
    private InMemoryProductRepository products;

    private Product cafe;

    @BeforeEach
    void oneProductOfAna() {
        products.clear();
        cafe = Product.create(UUID.randomUUID(), ANA, "Ana Ruiz", "Café pergamino seco", ProductCategory.CAFE,
                ProductUnit.ARROBA, Quantity.of("40"), Money.ofCents(8_500_000), "Garzón",
                Instant.parse("2026-11-01T15:00:00Z"));
        products.add(cafe);
    }

    private static String token(String role, UUID subject) {
        return "Bearer " + TestTokens.user(role).claim("sub", subject.toString()).signed();
    }

    private ResultActions upload(UUID id, String authorization, byte[] content) throws Exception {
        // The declared name and type lie on purpose: only the file signature counts.
        MockMultipartFile file = new MockMultipartFile("file", "foto.jpg", "image/jpeg", content);
        return http.perform(multipart(PHOTO, id).file(file).header("Authorization", authorization));
    }

    private ResultActions remove(UUID id, String authorization) throws Exception {
        return http.perform(delete(PHOTO, id).header("Authorization", authorization));
    }

    private static void expectError(ResultActions result, int status, String error, String message) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(jsonPath("$.error").value(error))
                .andExpect(jsonPath("$.message").value(message));
    }

    private String photoUrlAfter(ResultActions upload) throws Exception {
        return JsonPath.read(upload.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                "$.photoUrl");
    }

    @Test
    void theOwnerSetsThePhotoAndAnyoneDownloadsItCachedForADay() throws Exception {
        String url = photoUrlAfter(upload(cafe.id(), token("PRODUCER", ANA), PNG)
                .andExpect(jsonPath("$.id").value(cafe.id().toString()))
                .andExpect(jsonPath("$.photoUrl").value(matchesPattern("^/media/[0-9a-f]{32}\\.png$"))));

        http.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("Cache-Control", "public, max-age=86400"))
                .andExpect(content().bytes(PNG));
        http.perform(get("/api/v1/catalog/products/{id}", cafe.id())).andExpect(jsonPath("$.photoUrl").value(url));
    }

    @Test
    void anotherPhotoReplacesItAndTheOldOneIsGone() throws Exception {
        String first = photoUrlAfter(upload(cafe.id(), token("PRODUCER", ANA), PNG));

        String second = photoUrlAfter(upload(cafe.id(), token("PRODUCER", ANA), JPEG));

        assertThat(second).matches("^/media/[0-9a-f]{32}\\.jpg$");
        expectError(http.perform(get(first)), 404, "NOT_FOUND", "El recurso solicitado no existe.");
        http.perform(get(second)).andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_JPEG));
    }

    @Test
    void aPdfNamedJpgIsUnsupported() throws Exception {
        expectError(upload(cafe.id(), token("PRODUCER", ANA), PDF), 415, "UNSUPPORTED_MEDIA_TYPE",
                "Formato no admitido: use JPG, PNG o WebP.");
    }

    @Test
    void withoutTheFilePartIsAValidationErrorOnFile() throws Exception {
        http.perform(multipart(PHOTO, cafe.id()).param("other", "x").header("Authorization", token("PRODUCER", ANA)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("file"));
        http.perform(post(PHOTO, cafe.id()).contentType(MediaType.APPLICATION_JSON).content("{}")
                        .header("Authorization", token("PRODUCER", ANA)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("file"));
    }

    @Test
    void notFoundComesBeforeNotOwner() throws Exception {
        expectError(upload(UUID.randomUUID(), token("PRODUCER", UUID.randomUUID()), PNG), 404, "PRODUCT_NOT_FOUND",
                "El producto no existe.");
        expectError(upload(cafe.id(), token("PRODUCER", UUID.randomUUID()), PDF), 403, "NOT_PRODUCT_OWNER",
                "El producto pertenece a otro productor.");
    }

    @Test
    void onlyAProducerWithATokenMayChangeIt() throws Exception {
        upload(cafe.id(), token("BUYER", ANA), PNG).andExpect(status().isForbidden());
        http.perform(multipart(PHOTO, cafe.id()).file(new MockMultipartFile("file", PNG)))
                .andExpect(status().isUnauthorized());
        http.perform(delete(PHOTO, cafe.id())).andExpect(status().isUnauthorized());
    }

    @Test
    void removingThePhotoIs204AndItsBinaryIsGone() throws Exception {
        String url = photoUrlAfter(upload(cafe.id(), token("PRODUCER", ANA), PNG));

        remove(cafe.id(), token("PRODUCER", ANA)).andExpect(status().isNoContent());

        http.perform(get("/api/v1/catalog/products/{id}", cafe.id())).andExpect(jsonPath("$.photoUrl").value(nullValue()));
        http.perform(get(url)).andExpect(status().isNotFound());
        remove(cafe.id(), token("PRODUCER", ANA)).andExpect(status().isNoContent());
    }

    @Test
    void removingFollowsTheSameOrderOfErrors() throws Exception {
        expectError(remove(UUID.randomUUID(), token("PRODUCER", UUID.randomUUID())), 404, "PRODUCT_NOT_FOUND",
                "El producto no existe.");
        expectError(remove(cafe.id(), token("PRODUCER", UUID.randomUUID())), 403, "NOT_PRODUCT_OWNER",
                "El producto pertenece a otro productor.");
    }

    @Test
    void aMediaKeyThatDoesNotExistOrIsMalformedIs404() throws Exception {
        expectError(http.perform(get("/media/0123456789abcdef0123456789abcdef.png")), 404, "NOT_FOUND",
                "El recurso solicitado no existe.");
        http.perform(get("/media/catalog.png")).andExpect(status().isNotFound());
        http.perform(get("/media/0123456789ABCDEF0123456789ABCDEF.png")).andExpect(status().isNotFound());
    }
}
