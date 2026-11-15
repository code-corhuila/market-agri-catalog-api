package co.edu.corhuila.marketagri.catalog.app;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.marketagri.catalog.domain.model.Money;
import co.edu.corhuila.marketagri.catalog.domain.model.Product;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductCategory;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductUnit;
import co.edu.corhuila.marketagri.catalog.domain.model.Quantity;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

/** E-10 and E-11 over HTTP, with the repository in memory (Anexo C, "HTTP"). Both are anonymous. */
@SpringBootTest
@AutoConfigureMockMvc
class CatalogBrowseTest extends CatalogSpringTest {

    private static final String PRODUCTS = "/api/v1/catalog/products";
    private static final UUID OLDEST = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SAME_TIME_LOW_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID SAME_TIME_HIGH_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @TestConfiguration
    static class InMemory {
        @Bean
        @Primary
        InMemoryProductRepository inMemoryProducts() {
            return new InMemoryProductRepository();
        }
    }

    @Autowired
    private MockMvc http;

    @Autowired
    private InMemoryProductRepository products;

    private static Product product(UUID id, String quantity, String createdAt) {
        return Product.restore(id, UUID.fromString("a1b2c3d4-4444-0000-0000-000000000001"), "Ana Ruiz",
                "Café pergamino seco", ProductCategory.CAFE, ProductUnit.ARROBA, Quantity.of(quantity),
                Money.ofCents(8_500_000), "Garzón", null, Instant.parse(createdAt), Instant.parse(createdAt));
    }

    @BeforeEach
    void threeProducts() {
        products.clear();
        products.add(product(OLDEST, "40", "2026-10-01T10:00:00Z"));
        products.add(product(SAME_TIME_HIGH_ID, "0", "2026-10-02T10:00:00Z"));
        products.add(product(SAME_TIME_LOW_ID, "12.5", "2026-10-02T10:00:00Z"));
    }

    @Test
    void listIsNewestFirstThenByIdWithTheDefaultPage() throws Exception {
        http.perform(get(PRODUCTS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[0].id").value(SAME_TIME_LOW_ID.toString()))
                .andExpect(jsonPath("$.data[1].id").value(SAME_TIME_HIGH_ID.toString()))
                .andExpect(jsonPath("$.data[2].id").value(OLDEST.toString()))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.limit").value(20))
                .andExpect(jsonPath("$.meta.total").value(3))
                .andExpect(jsonPath("$.meta.totalPages").value(1));
    }

    @Test
    void secondPageOfTwo() throws Exception {
        http.perform(get(PRODUCTS).param("page", "2").param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(OLDEST.toString()))
                .andExpect(jsonPath("$.meta.page").value(2))
                .andExpect(jsonPath("$.meta.totalPages").value(2));
    }

    @Test
    void pagePastTheLastIsEmptyWithTheRealTotal() throws Exception {
        http.perform(get(PRODUCTS).param("page", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)))
                .andExpect(jsonPath("$.meta.total").value(3));
    }

    @Test
    void productViewHasTheContractShape() throws Exception {
        http.perform(get(PRODUCTS + "/" + SAME_TIME_LOW_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SAME_TIME_LOW_ID.toString()))
                .andExpect(jsonPath("$.producerId").value("a1b2c3d4-4444-0000-0000-000000000001"))
                .andExpect(jsonPath("$.producerName").value("Ana Ruiz"))
                .andExpect(jsonPath("$.name").value("Café pergamino seco"))
                .andExpect(jsonPath("$.category").value("CAFE"))
                .andExpect(jsonPath("$.unit").value("ARROBA"))
                .andExpect(jsonPath("$.quantity").value(12.5))
                .andExpect(jsonPath("$.priceCents").value(8_500_000))
                .andExpect(jsonPath("$.currency").value("COP"))
                .andExpect(jsonPath("$.municipality").value("Garzón"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$", hasKey("photoUrl")))
                .andExpect(jsonPath("$.photoUrl").value(nullValue()))
                .andExpect(jsonPath("$.createdAt").value("2026-10-02T10:00:00Z"))
                .andExpect(jsonPath("$.updatedAt").value("2026-10-02T10:00:00Z"));
    }

    @Test
    void statusFollowsTheQuantity() throws Exception {
        http.perform(get(PRODUCTS + "/" + SAME_TIME_HIGH_ID))
                .andExpect(jsonPath("$.status").value("OUT_OF_STOCK"));
    }

    @Test
    void unknownProductIs404ProductNotFound() throws Exception {
        http.perform(get(PRODUCTS + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("PRODUCT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("El producto no existe."))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void idThatIsNotAUuidIs400() throws Exception {
        http.perform(get(PRODUCTS + "/no-es-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("id"));
    }

    @Test
    void limitOutOfRangeIs400() throws Exception {
        for (String limit : new String[] {"0", "101", "abc"}) {
            http.perform(get(PRODUCTS).param("limit", limit))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.details[0].field").value("limit"));
        }
    }

    @Test
    void pageBelowOneIs400() throws Exception {
        http.perform(get(PRODUCTS).param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("page"));
    }

    @Test
    void unknownParameterIs400() throws Exception {
        http.perform(get(PRODUCTS).param("color", "rojo"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("color"));
    }

    @Test
    void anonymousEvenWithAnExpiredToken() throws Exception {
        http.perform(get(PRODUCTS).header("Authorization",
                        "Bearer " + TestTokens.user("BUYER").expiredSecondsAgo(3600).signed()))
                .andExpect(status().isOk());
    }
}
