package co.edu.corhuila.marketagri.catalog.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.marketagri.catalog.application.port.in.PublishProduct;
import co.edu.corhuila.marketagri.catalog.application.port.in.PublishProduct.Command;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductCategory;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductUnit;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * E-13 against the schema of catalog-db: the row satisfies every CHECK (municipality_key, D-16),
 * and a repeated key — also a concurrent one — leaves one product (D-C35). Not
 * {@code @Transactional}: the rollback of a replay must be real. Products are left deleted.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class CatalogPublishIT extends CatalogSpringTest {

    private static final int THREADS = 6;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TEST_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> "");
        registry.add("spring.datasource.password", () -> "");
    }

    @Autowired
    private PublishProduct publish;

    @Autowired
    private JdbcClient jdbc;

    private final UUID producer = UUID.randomUUID();

    private Command command(String key) {
        return new Command(producer, "Ana Ruiz", key, " Café pergamino seco ", ProductCategory.CAFE,
                ProductUnit.ARROBA, new BigDecimal("12.5"), 8_500_000, "San Agustín");
    }

    @AfterEach
    void leaveThemDeleted() {
        jdbc.sql("UPDATE catalog.product SET deleted_at = now() WHERE producer_id = :p").param("p", producer).update();
    }

    private long productsOfTheProducer() {
        return jdbc.sql("SELECT count(*) FROM catalog.product WHERE producer_id = :p")
                .param("p", producer).query(Long.class).single();
    }

    @Test
    void publishedRowHasTheNormalizedMunicipalityAndTheKey() {
        UUID id = publish.publish(command("key-round-trip")).product().id();

        assertThat(jdbc.sql("""
                SELECT name, municipality, municipality_key, status, price_cents, quantity
                FROM catalog.product WHERE id = :id""").param("id", id).query().singleRow())
                .containsEntry("name", "Café pergamino seco")
                .containsEntry("municipality", "San Agustín")
                .containsEntry("municipality_key", "san agustin")
                .containsEntry("status", "ACTIVE")
                .containsEntry("price_cents", 8_500_000L)
                .containsEntry("quantity", new BigDecimal("12.50"));
        assertThat(jdbc.sql("SELECT product_id FROM catalog.idempotency_key WHERE owner_id = :p")
                .param("p", producer).query(UUID.class).single()).isEqualTo(id);
    }

    @Test
    void replayRollsBackAndAnswersTheOriginal() {
        UUID original = publish.publish(command("key-replay-1")).product().id();

        PublishProduct.Publication replay = publish.publish(command("key-replay-1"));

        assertThat(replay.created()).isFalse();
        assertThat(replay.product().id()).isEqualTo(original);
        assertThat(productsOfTheProducer()).isEqualTo(1L);
    }

    @Test
    void concurrentRequestsWithTheSameKeyLeaveOneProduct() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<UUID>> results = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return publish.publish(command("key-concurrent")).product().id();
            }));
        }
        start.countDown();
        pool.shutdown();

        Set<UUID> answered = new HashSet<>();
        for (Future<UUID> result : results) {
            answered.add(result.get());
        }

        assertThat(answered).hasSize(1);
        assertThat(productsOfTheProducer()).isEqualTo(1L);
    }
}
