package co.edu.corhuila.marketagri.catalog.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * E-18 over HTTP (HU-CAT-003, D-C5): logical and terminal, 404 everywhere afterwards, the photo binary
 * deleted, and every deletion in the log (NFR-052). Product ports in memory, photos in a temporary folder.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class CatalogDeleteTest extends CatalogSpringTest {

    private static final String PRODUCTS = "/api/v1/catalog/products";
    private static final UUID ANA = UUID.fromString("0b6f2c7e-4a51-4c1e-9d0e-2f1c3a7b9e10");
    private static final UUID LUIS = UUID.fromString("3c2b1a09-8f7e-4d6c-9b5a-4e3d2c1b0a99");
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
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

    private ResultActions remove(String id, String authorization) throws Exception {
        var request = delete(PRODUCTS + "/" + id);
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        return http.perform(request);
    }

    @Test
    void ownerDeletesAndTheProductIsGoneEverywhere() throws Exception {
        remove(cafe.id().toString(), token("PRODUCER", ANA)).andExpect(status().isNoContent());

        http.perform(get(PRODUCTS + "/" + cafe.id()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("PRODUCT_NOT_FOUND"));
        http.perform(get(PRODUCTS)).andExpect(jsonPath("$.data", hasSize(0)));
        http.perform(get(PRODUCTS + "/mine").header("Authorization", token("PRODUCER", ANA)))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void deletingItASecondTimeIs404() throws Exception {
        remove(cafe.id().toString(), token("PRODUCER", ANA)).andExpect(status().isNoContent());

        remove(cafe.id().toString(), token("PRODUCER", ANA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("PRODUCT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("El producto no existe."));
    }

    @Test
    void someoneElsesProductIs403AndStays() throws Exception {
        remove(cafe.id().toString(), token("PRODUCER", LUIS))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("NOT_PRODUCT_OWNER"))
                .andExpect(jsonPath("$.message").value("El producto pertenece a otro productor."));

        http.perform(get(PRODUCTS + "/" + cafe.id())).andExpect(status().isOk());
    }

    @Test
    void unknownProductIs404BeforeTheOwner() throws Exception {
        remove(UUID.randomUUID().toString(), token("PRODUCER", LUIS))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void thePhotoBinaryIsDeletedWithTheProduct() throws Exception {
        String photoUrl = JsonPath.read(http.perform(multipart(PRODUCTS + "/{id}/photo", cafe.id())
                        .file(new MockMultipartFile("file", "foto.png", "image/png", PNG))
                        .header("Authorization", token("PRODUCER", ANA)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.photoUrl");
        Path binary = MEDIA.resolve(photoUrl.substring("/media/".length()));
        assertThat(binary).exists();

        remove(cafe.id().toString(), token("PRODUCER", ANA)).andExpect(status().isNoContent());

        assertThat(binary).doesNotExist();
    }

    @Test
    void everyDeletionIsLoggedWithWhoAndWhichProduct(CapturedOutput log) throws Exception {
        remove(cafe.id().toString(), token("PRODUCER", ANA)).andExpect(status().isNoContent());

        assertThat(log.getOut()).contains("Product " + cafe.id() + " deleted by producer " + ANA);
    }

    @Test
    void buyerIsForbiddenAnonymousUnauthorizedAndABadIdIs400() throws Exception {
        remove(cafe.id().toString(), token("BUYER", ANA)).andExpect(status().isForbidden());
        remove(cafe.id().toString(), null).andExpect(status().isUnauthorized());
        remove("no-es-uuid", token("PRODUCER", ANA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("id"));
        http.perform(get(PRODUCTS + "/" + cafe.id())).andExpect(status().isOk());
    }
}
