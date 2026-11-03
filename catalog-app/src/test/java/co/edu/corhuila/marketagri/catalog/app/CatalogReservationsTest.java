package co.edu.corhuila.marketagri.catalog.app;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

/**
 * I-02, I-03 and I-04 over HTTP with the ports in memory (Anexo C, "HTTP"): the cards of
 * api-contract §4.2, the errors of I-03 in their order, and the token rule of {@code /internal/**}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CatalogReservationsTest extends CatalogSpringTest {

    private static final String INTERNAL = "/internal/v1/catalog";
    private static final UUID CAFE = UUID.fromString("00000000-0000-0000-0000-0000000000ca");
    private static final UUID PRODUCER = UUID.fromString("a1b2c3d4-4444-0000-0000-000000000001");
    private static final UUID TRANSACTION = UUID.fromString("7f000000-0000-0000-0000-000000000001");

    @TestConfiguration
    static class InMemory {
        @Bean
        @Primary
        InMemoryProductRepository inMemoryProducts() {
            return new InMemoryProductRepository();
        }

        @Bean
        @Primary
        InMemoryReservationRepository inMemoryReservations() {
            return new InMemoryReservationRepository();
        }

        /** No database in these tests: the work runs as it is. */
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

    @Autowired
    private InMemoryReservationRepository reservations;

    private void stored(UUID id, String quantity) {
        Instant created = Instant.parse("2026-10-02T10:00:00Z");
        products.add(Product.restore(id, PRODUCER, "Ana Ruiz", "Café pergamino seco", ProductCategory.CAFE,
                ProductUnit.ARROBA, Quantity.of(quantity), Money.ofCents(8_500_000), "Garzón", null, created,
                created));
    }

    @BeforeEach
    void cafeWithTenArrobas() {
        products.clear();
        reservations.clear();
        stored(CAFE, "10");
    }

    private ResultActions reserve(UUID product, String body) throws Exception {
        return http.perform(post(INTERNAL + "/products/" + product + "/reservations")
                .header("Authorization", "Bearer " + TestTokens.service().signed())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String body(UUID transactionId, String quantity) {
        return "{\"transactionId\":\"" + transactionId + "\",\"quantity\":" + quantity + "}";
    }

    private ResultActions release(UUID transactionId) throws Exception {
        return http.perform(delete(INTERNAL + "/reservations/" + transactionId)
                .header("Authorization", "Bearer " + TestTokens.service().signed()));
    }

    private ResultActions readProduct(String token) throws Exception {
        return http.perform(get(INTERNAL + "/products/" + CAFE).header("Authorization", "Bearer " + token));
    }

    private static void expectError(ResultActions result, int status, String error, String message) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(jsonPath("$.error").value(error))
                .andExpect(jsonPath("$.message").value(message));
    }

    @Test
    void serviceReadsAProductWithI02() throws Exception {
        readProduct(TestTokens.service().signed())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CAFE.toString()))
                .andExpect(jsonPath("$.priceCents").value(8_500_000))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void userTokenOnI02IsForbidden() throws Exception {
        readProduct(TestTokens.user("BUYER").signed()).andExpect(status().isForbidden());
    }

    @Test
    void noTokenOnI03IsUnauthorized() throws Exception {
        http.perform(post(INTERNAL + "/products/" + CAFE + "/reservations")
                        .contentType(MediaType.APPLICATION_JSON).content(body(TRANSACTION, "4")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownProductOnI02IsNotFound() throws Exception {
        expectError(http.perform(get(INTERNAL + "/products/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + TestTokens.service().signed())),
                404, "PRODUCT_NOT_FOUND", "El producto no existe.");
    }

    @Test
    void firstReservationIs201WithTheSnapshot() throws Exception {
        reserve(CAFE, body(TRANSACTION, "4"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionId").value(TRANSACTION.toString()))
                .andExpect(jsonPath("$.productId").value(CAFE.toString()))
                .andExpect(jsonPath("$.productName").value("Café pergamino seco"))
                .andExpect(jsonPath("$.producerId").value(PRODUCER.toString()))
                .andExpect(jsonPath("$.quantity").value(4.0))
                .andExpect(jsonPath("$.unit").value("ARROBA"))
                .andExpect(jsonPath("$.unitPriceCents").value(8_500_000))
                .andExpect(jsonPath("$.currency").value("COP"))
                .andExpect(jsonPath("$.remainingQuantity").value(6.0))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void retryIs200AndSubtractsNothing() throws Exception {
        reserve(CAFE, body(TRANSACTION, "4")).andExpect(status().isCreated());

        reserve(CAFE, body(TRANSACTION, "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingQuantity").value(6.0));
        readProduct(TestTokens.service().signed()).andExpect(jsonPath("$.quantity").value(6.0));
    }

    @Test
    void missingTransactionIdIsAValidationError() throws Exception {
        reserve(CAFE, "{\"quantity\":4}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("transactionId"));
    }

    @Test
    void moreThanTwoDecimalsIsAValidationError() throws Exception {
        reserve(CAFE, body(TRANSACTION, "1.005"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("quantity"));
    }

    @Test
    void zeroOrNegativeIsNotPositiveEvenForAnUnknownProduct() throws Exception {
        expectError(reserve(CAFE, body(TRANSACTION, "0")), 422, "QUANTITY_NOT_POSITIVE",
                "La cantidad debe ser mayor que cero.");
        expectError(reserve(UUID.randomUUID(), body(TRANSACTION, "-1")), 422, "QUANTITY_NOT_POSITIVE",
                "La cantidad debe ser mayor que cero.");
    }

    @Test
    void unknownProductIsNotFound() throws Exception {
        expectError(reserve(UUID.randomUUID(), body(TRANSACTION, "1")), 404, "PRODUCT_NOT_FOUND",
                "El producto no existe.");
    }

    @Test
    void sameTransactionWithOtherDataIsAMismatch() throws Exception {
        reserve(CAFE, body(TRANSACTION, "4")).andExpect(status().isCreated());

        expectError(reserve(CAFE, body(TRANSACTION, "5")), 409, "RESERVATION_MISMATCH",
                "La reserva ya existe con otros datos.");
    }

    @Test
    void productOutOfStockIsNotAvailable() throws Exception {
        UUID empty = UUID.randomUUID();
        stored(empty, "0");

        expectError(reserve(empty, body(TRANSACTION, "1")), 409, "PRODUCT_NOT_AVAILABLE",
                "El producto no está disponible para la venta.");
    }

    @Test
    void moreThanAvailableNamesBothQuantities() throws Exception {
        expectError(reserve(CAFE, body(TRANSACTION, "12.5")), 409, "INSUFFICIENT_STOCK",
                "Cantidad insuficiente: hay 10 disponibles y se solicitaron 12.5.");
    }

    @Test
    void releaseIs204AndGivesTheStockBackOnce() throws Exception {
        reserve(CAFE, body(TRANSACTION, "10")).andExpect(status().isCreated());

        release(TRANSACTION).andExpect(status().isNoContent()).andExpect(content().string(""));
        release(TRANSACTION).andExpect(status().isNoContent());

        readProduct(TestTokens.service().signed())
                .andExpect(jsonPath("$.quantity").value(10.0))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void releaseOfAnUnknownTransactionIs204() throws Exception {
        release(UUID.randomUUID()).andExpect(status().isNoContent());
    }
}
