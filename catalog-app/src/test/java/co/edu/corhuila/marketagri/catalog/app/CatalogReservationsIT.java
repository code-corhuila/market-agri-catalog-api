package co.edu.corhuila.marketagri.catalog.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.marketagri.catalog.application.port.in.ReleaseStock;
import co.edu.corhuila.marketagri.catalog.application.port.in.ReserveStock;
import co.edu.corhuila.marketagri.catalog.domain.model.InsufficientStockException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * I-03 and I-04 against the schema of catalog-db, with real concurrency: several threads, each with
 * its own connection and transaction. They must commit, so this class is not {@code @Transactional}:
 * catalog_app has no DELETE (D-14), so every product is left deleted ({@code deleted_at}) at the end.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class CatalogReservationsIT extends CatalogSpringTest {

    private static final int THREADS = 8;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TEST_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> "");
        registry.add("spring.datasource.password", () -> "");
    }

    @Autowired
    private ReserveStock reserve;

    @Autowired
    private ReleaseStock release;

    @Autowired
    private JdbcClient jdbc;

    private UUID product;

    @BeforeEach
    void productWithTenArrobas() {
        product = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO catalog.product (id, producer_id, producer_name, name, category, unit, quantity,
                    price_cents, municipality, municipality_key, status)
                VALUES (:id, gen_random_uuid(), 'Ana Ruiz', 'Café pergamino seco', 'CAFE', 'ARROBA', 10,
                    8500000, 'Garzón', 'garzon', 'ACTIVE')""")
                .param("id", product)
                .update();
    }

    @AfterEach
    void leaveItDeleted() {
        jdbc.sql("UPDATE catalog.product SET deleted_at = now() WHERE id = :id").param("id", product).update();
    }

    private Map<String, Object> productRow() {
        return jdbc.sql("SELECT quantity, status FROM catalog.product WHERE id = :id")
                .param("id", product).query().singleRow();
    }

    /** Starts every task at the same moment and waits for all of them. */
    private static <T> List<Future<T>> together(List<Callable<T>> tasks) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> results = new ArrayList<>();
        for (Callable<T> task : tasks) {
            results.add(pool.submit(() -> {
                start.await();
                return task.call();
            }));
        }
        start.countDown();
        pool.shutdown();
        return results;
    }

    @Test
    void concurrentReservationsNeverTakeMoreThanTheStock() throws Exception {
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tasks.add(() -> {
                try {
                    reserve.reserve(product, UUID.randomUUID(), new BigDecimal("3"));
                    return true;
                } catch (InsufficientStockException refused) {
                    return false;
                }
            });
        }

        int accepted = 0;
        for (Future<Boolean> result : together(tasks)) {
            accepted += result.get() ? 1 : 0;
        }

        assertThat(accepted).isEqualTo(3);
        assertThat(productRow()).containsEntry("quantity", new BigDecimal("1.00")).containsEntry("status", "ACTIVE");
        assertThat(jdbc.sql("SELECT count(*) FROM catalog.stock_reservation WHERE product_id = :id")
                .param("id", product).query(Long.class).single()).isEqualTo(3L);
    }

    @Test
    void lastUnitsStoreTheSnapshotAndARetrySubtractsNothing() {
        UUID transaction = UUID.randomUUID();

        assertThat(reserve.reserve(product, transaction, new BigDecimal("10")).created()).isTrue();
        assertThat(reserve.reserve(product, transaction, new BigDecimal("10")).created()).isFalse();

        assertThat(productRow()).containsEntry("quantity", new BigDecimal("0.00"))
                .containsEntry("status", "OUT_OF_STOCK");
        assertThat(jdbc.sql("""
                SELECT unit_price_cents, remaining_quantity, product_name, released_at
                FROM catalog.stock_reservation WHERE transaction_id = :id""")
                .param("id", transaction).query().singleRow())
                .containsEntry("unit_price_cents", 8_500_000L)
                .containsEntry("remaining_quantity", new BigDecimal("0.00"))
                .containsEntry("product_name", "Café pergamino seco")
                .containsEntry("released_at", null);
    }

    @Test
    void concurrentReleasesGiveTheStockBackOnce() throws Exception {
        UUID transaction = UUID.randomUUID();
        reserve.reserve(product, transaction, new BigDecimal("10"));
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tasks.add(() -> {
                release.release(transaction);
                return null;
            });
        }

        for (Future<Void> result : together(tasks)) {
            result.get();
        }

        assertThat(productRow()).containsEntry("quantity", new BigDecimal("10.00")).containsEntry("status", "ACTIVE");
        assertThat(jdbc.sql("SELECT released_at IS NOT NULL FROM catalog.stock_reservation WHERE transaction_id = :id")
                .param("id", transaction).query(Boolean.class).single()).isTrue();
    }
}
