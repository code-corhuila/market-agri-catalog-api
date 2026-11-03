package co.edu.corhuila.marketagri.catalog.app;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.marketagri.catalog.application.port.out.UnitOfWork;
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

/**
 * E-13 over HTTP with the ports in memory (Anexo C, "HTTP"): the card of api-contract §4.2, the
 * {@code Idempotency-Key} of D-C35, the role rule and the order of the errors.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CatalogPublishTest extends CatalogSpringTest {

    private static final String PRODUCTS = "/api/v1/catalog/products";
    private static final String ANA = "a1b2c3d4-4444-0000-0000-000000000001";
    private static final String KEY = "7c0b6a1e-key";

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

    @BeforeEach
    void emptyCatalog() {
        products.clear();
    }

    private static String body(String quantity, String priceCents) {
        return """
                {"name":"  Café pergamino seco ","category":"CAFE","unit":"ARROBA","quantity":%s,
                 "priceCents":%s,"municipality":"San Agustín","status":"OUT_OF_STOCK","color":"verde"}"""
                .formatted(quantity, priceCents);
    }

    private ResultActions publish(String token, String key, String body) throws Exception {
        var request = post(PRODUCTS).contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return http.perform(request);
    }

    private static String ana() {
        return TestTokens.user("PRODUCER").claim("sub", ANA).signed();
    }

    private static void expectError(ResultActions result, int status, String error, String message) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(jsonPath("$.error").value(error))
                .andExpect(jsonPath("$.message").value(message));
    }

    @Test
    void producerPublishesAndGets201WithLocation() throws Exception {
        String location = publish(ana(), KEY, body("40", "8500000"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Café pergamino seco"))
                .andExpect(jsonPath("$.producerId").value(ANA))
                .andExpect(jsonPath("$.producerName").value("Ana Ruiz"))
                .andExpect(jsonPath("$.priceCents").value(8_500_000))
                .andExpect(jsonPath("$.currency").value("COP"))
                .andExpect(jsonPath("$.municipality").value("San Agustín"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.photoUrl").value(nullValue()))
                .andReturn().getResponse().getHeader("Location");

        http.perform(get(location)).andExpect(status().isOk()).andExpect(jsonPath("$.quantity").value(40.0));
    }

    @Test
    void zeroQuantityIsPublishedOutOfStock() throws Exception {
        publish(ana(), KEY, body("0", "8500000"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OUT_OF_STOCK"));
    }

    @Test
    void sameKeyAgainIs200WithTheOriginal() throws Exception {
        String id = publish(ana(), KEY, body("40", "8500000")).andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location").replace(PRODUCTS + "/", "");

        publish(ana(), KEY, body("5", "1000"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.priceCents").value(8_500_000));
        http.perform(get(PRODUCTS)).andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    void anotherProducerWithTheSameKeyPublishesAnother() throws Exception {
        publish(ana(), KEY, body("40", "8500000")).andExpect(status().isCreated());

        publish(TestTokens.user("PRODUCER").claim("sub", UUID.randomUUID().toString()).signed(), KEY,
                body("40", "8500000")).andExpect(status().isCreated());
    }

    @Test
    void buyerAndAdminAreForbidden() throws Exception {
        publish(TestTokens.user("BUYER").signed(), KEY, body("40", "8500000")).andExpect(status().isForbidden());
        publish(TestTokens.user("ADMIN").signed(), KEY, body("40", "8500000")).andExpect(status().isForbidden());
    }

    @Test
    void withoutTokenIsUnauthorized() throws Exception {
        publish(null, KEY, body("40", "8500000")).andExpect(status().isUnauthorized());
    }

    @Test
    void missingOrShortIdempotencyKeyIsAValidationError() throws Exception {
        publish(ana(), null, body("40", "8500000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("Idempotency-Key"));
        publish(ana(), "short", body("40", "8500000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("Idempotency-Key"));
    }

    @Test
    void priceWithDecimalsIsAValidationErrorNotTwelve() throws Exception {
        publish(ana(), KEY, body("40", "12.5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void unknownCategoryIsAValidationError() throws Exception {
        publish(ana(), KEY, body("40", "8500000").replace("\"CAFE\"", "\"FLORES\""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shapeErrorsNameTheField() throws Exception {
        publish(ana(), KEY, body("40.005", "8500000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("quantity"));
        publish(ana(), KEY, body("40", "8500000").replace("  Café pergamino seco ", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("name"));
        publish(ana(), KEY, body("40", "8500000").replace("San Agustín", "(?)"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("municipality"));
    }

    @Test
    void priceNotPositiveComesBeforeQuantityNegative() throws Exception {
        expectError(publish(ana(), KEY, body("-1", "0")), 422, "PRICE_NOT_POSITIVE",
                "El precio debe ser mayor que cero.");
        expectError(publish(ana(), KEY, body("-1", "100")), 422, "QUANTITY_NEGATIVE",
                "La cantidad no puede ser negativa.");
    }
}
