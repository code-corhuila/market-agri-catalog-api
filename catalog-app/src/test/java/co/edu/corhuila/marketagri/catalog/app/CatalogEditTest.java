package co.edu.corhuila.marketagri.catalog.app;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.marketagri.catalog.application.port.out.UnitOfWork;
import co.edu.corhuila.marketagri.catalog.domain.model.Money;
import co.edu.corhuila.marketagri.catalog.domain.model.Product;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductCategory;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductUnit;
import co.edu.corhuila.marketagri.catalog.domain.model.Quantity;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** E-14 (the owner edits) and E-12 (my products) over HTTP, with the repository in memory (HU-CAT-002). */
@SpringBootTest
@AutoConfigureMockMvc
class CatalogEditTest extends CatalogSpringTest {

    private static final String PRODUCTS = "/api/v1/catalog/products";
    private static final String MINE = PRODUCTS + "/mine";
    private static final UUID ANA = UUID.fromString("a1b2c3d4-4444-0000-0000-000000000001");
    private static final UUID LUIS = UUID.fromString("a1b2c3d4-4444-0000-0000-000000000002");
    private static final UUID ANA_OLD = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ANA_NEW_SOLD_OUT = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID LUIS_PRODUCT = UUID.fromString("00000000-0000-0000-0000-000000000003");

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

    private static Product product(UUID id, UUID producer, String producerName, String quantity, String createdAt) {
        return Product.restore(id, producer, producerName, "Café pergamino seco", ProductCategory.CAFE,
                ProductUnit.ARROBA, Quantity.of(quantity), Money.ofCents(8_500_000), "Garzón", null,
                Instant.parse(createdAt), Instant.parse(createdAt));
    }

    @BeforeEach
    void twoProducers() {
        products.clear();
        products.add(product(ANA_OLD, ANA, "Ana Ruiz", "40", "2026-10-01T10:00:00Z"));
        products.add(product(ANA_NEW_SOLD_OUT, ANA, "Ana Ruiz", "0", "2026-10-03T10:00:00Z"));
        products.add(product(LUIS_PRODUCT, LUIS, "Luis Gómez", "8", "2026-10-02T10:00:00Z"));
    }

    private static String token(UUID producer) {
        return TestTokens.user("PRODUCER").claim("sub", producer.toString()).signed();
    }

    private static String body(String quantity, String priceCents) {
        return """
                {"name":"  Café lavado ","category":"CACAO","unit":"KILOGRAMO","quantity":%s,
                 "priceCents":%s,"municipality":" Pitalito ","status":"ACTIVE"}""".formatted(quantity, priceCents);
    }

    private ResultActions edit(String token, String id, String body) throws Exception {
        var request = put(PRODUCTS + "/" + id).contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return http.perform(request);
    }

    private ResultActions mine(String token, String... parameters) throws Exception {
        var request = get(MINE);
        for (int i = 0; i < parameters.length; i += 2) {
            request.param(parameters[i], parameters[i + 1]);
        }
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return http.perform(request);
    }

    @Test
    void ownerEditsToZeroAndGetsOutOfStock() throws Exception {
        edit(token(ANA), ANA_OLD.toString(), body("0", "9000000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ANA_OLD.toString()))
                .andExpect(jsonPath("$.producerId").value(ANA.toString()))
                .andExpect(jsonPath("$.name").value("Café lavado"))
                .andExpect(jsonPath("$.category").value("CACAO"))
                .andExpect(jsonPath("$.unit").value("KILOGRAMO"))
                .andExpect(jsonPath("$.priceCents").value(9_000_000))
                .andExpect(jsonPath("$.municipality").value("Pitalito"))
                .andExpect(jsonPath("$.status").value("OUT_OF_STOCK"))
                .andExpect(jsonPath("$.createdAt").value("2026-10-01T10:00:00Z"));

        http.perform(get(PRODUCTS + "/" + ANA_OLD)).andExpect(jsonPath("$.status").value("OUT_OF_STOCK"));
    }

    @Test
    void ownerEditFromZeroMakesItActive() throws Exception {
        edit(token(ANA), ANA_NEW_SOLD_OUT.toString(), body("12.5", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(12.5))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void someoneElsesProductIs403AndNothingChanges() throws Exception {
        edit(token(ANA), LUIS_PRODUCT.toString(), body("1", "100"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("NOT_PRODUCT_OWNER"))
                .andExpect(jsonPath("$.message").value("El producto pertenece a otro productor."));

        http.perform(get(PRODUCTS + "/" + LUIS_PRODUCT))
                .andExpect(jsonPath("$.name").value("Café pergamino seco"))
                .andExpect(jsonPath("$.quantity").value(8.0));
    }

    @Test
    void unknownProductIs404() throws Exception {
        edit(token(ANA), UUID.randomUUID().toString(), body("1", "100"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("PRODUCT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("El producto no existe."));
    }

    @Test
    void errorsComeInTheOrderOfTheContract() throws Exception {
        edit(token(ANA), UUID.randomUUID().toString(), "{\"name\":\"Café\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        edit(token(ANA), LUIS_PRODUCT.toString(), body("-1", "0"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("NOT_PRODUCT_OWNER"));
        edit(token(ANA), ANA_OLD.toString(), body("-1", "0"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("PRICE_NOT_POSITIVE"));
        edit(token(ANA), ANA_OLD.toString(), body("-1", "100"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("QUANTITY_NEGATIVE"));
    }

    @Test
    void idThatIsNotAUuidIs400() throws Exception {
        edit(token(ANA), "no-es-uuid", body("1", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("id"));
    }

    @Test
    void buyerIsForbiddenAndAnonymousUnauthorized() throws Exception {
        String buyer = TestTokens.user("BUYER").signed();
        edit(buyer, ANA_OLD.toString(), body("1", "100")).andExpect(status().isForbidden());
        mine(buyer).andExpect(status().isForbidden());
        edit(null, ANA_OLD.toString(), body("1", "100")).andExpect(status().isUnauthorized());
        mine(null).andExpect(status().isUnauthorized());
    }

    @Test
    void mineIsOnlyMyProductsNewestFirstWithBothStatuses() throws Exception {
        mine(token(ANA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id").value(contains(ANA_NEW_SOLD_OUT.toString(), ANA_OLD.toString())))
                .andExpect(jsonPath("$.meta.total").value(2));
        mine(token(LUIS))
                .andExpect(jsonPath("$.data[*].id").value(contains(LUIS_PRODUCT.toString())));
    }

    @Test
    void mineFiltersByStatusAndPages() throws Exception {
        mine(token(ANA), "status", "ACTIVE")
                .andExpect(jsonPath("$.data[*].id").value(contains(ANA_OLD.toString())));
        mine(token(ANA), "page", "2", "limit", "1")
                .andExpect(jsonPath("$.data[*].id").value(contains(ANA_OLD.toString())))
                .andExpect(jsonPath("$.meta.totalPages").value(2));
    }

    @Test
    void mineDoesNotAcceptTheFiltersOfTheCatalog() throws Exception {
        for (String parameter : new String[] {"category", "municipality"}) {
            mine(token(ANA), parameter, "CAFE")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details[0].field").value(parameter));
        }
        mine(token(ANA), "status", "SOLD")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("status"));
    }
}
