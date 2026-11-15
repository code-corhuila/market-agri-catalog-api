package co.edu.corhuila.marketagri.catalog.app;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import co.edu.corhuila.marketagri.catalog.application.port.out.UnitOfWork;
import co.edu.corhuila.marketagri.catalog.application.usecase.StockReservationService;
import co.edu.corhuila.marketagri.catalog.domain.model.Money;
import co.edu.corhuila.marketagri.catalog.domain.model.Product;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductCategory;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductUnit;
import co.edu.corhuila.marketagri.catalog.domain.model.Quantity;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.function.Supplier;
import org.apache.hc.core5.http.HttpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Verifies workflow's I-03 and I-04 Pact V4 against the real HTTP adapter. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Provider("catalog-api")
@PactFolder("pacts")
class CatalogPactProviderTest extends CatalogSpringTest {
    private static final UUID PRODUCT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PRODUCER = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");

    @TestConfiguration
    static class InMemory {
        @Bean @Primary InMemoryProductRepository products() { return new InMemoryProductRepository(); }
        @Bean @Primary InMemoryReservationRepository reservations() { return new InMemoryReservationRepository(); }
        @Bean @Primary UnitOfWork pactUnitOfWork() { return new UnitOfWork() {
            @Override public <T> T inTransaction(Supplier<T> work) { return work.get(); }
        }; }
        @Bean @Primary StockReservationService pactStockReservations(InMemoryProductRepository products,
                InMemoryReservationRepository reservations, UnitOfWork unitOfWork) {
            return new StockReservationService(products, reservations, unitOfWork,
                    Clock.fixed(NOW, ZoneOffset.UTC));
        }
    }

    @Value("${local.server.port}") private int port;
    @Autowired private InMemoryProductRepository products;
    @Autowired private InMemoryReservationRepository reservations;

    @BeforeEach
    void target(PactVerificationContext context) { context.setTarget(new HttpTestTarget("localhost", port)); }

    @State("an active product has enough stock")
    void activeProduct() {
        products.clear(); reservations.clear();
        products.add(Product.restore(PRODUCT, PRODUCER, "Luis", "Café", ProductCategory.CAFE,
                ProductUnit.KILOGRAMO, Quantity.of("10"), Money.ofCents(8_500_000), "Garzón", null, NOW, NOW));
    }

    @State("a reservation may exist or may already be released")
    void releasableReservation() { products.clear(); reservations.clear(); }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verifies(PactVerificationContext context, HttpRequest request) {
        request.setHeader("Authorization", "Bearer " + TestTokens.service().signed());
        context.verifyInteraction();
    }
}
