package co.edu.corhuila.marketagri.catalog.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.marketagri.catalog.application.port.in.PublishProduct.Command;
import co.edu.corhuila.marketagri.catalog.application.port.in.PublishProduct.Publication;
import co.edu.corhuila.marketagri.catalog.application.port.out.ProductPublications;
import co.edu.corhuila.marketagri.catalog.application.port.out.ProductRepository;
import co.edu.corhuila.marketagri.catalog.application.port.out.UnitOfWork;
import co.edu.corhuila.marketagri.catalog.domain.model.DomainException;
import co.edu.corhuila.marketagri.catalog.domain.model.Money;
import co.edu.corhuila.marketagri.catalog.domain.model.Page;
import co.edu.corhuila.marketagri.catalog.domain.model.PageRequest;
import co.edu.corhuila.marketagri.catalog.domain.model.Product;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductCategory;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductStatus;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductUnit;
import co.edu.corhuila.marketagri.catalog.domain.model.Quantity;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** E-13 in the core (D-C35): one transaction, the replay rolls back, and the order of the 422s. */
class PublishProductServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
    private static final UUID ANA = UUID.fromString("a1b2c3d4-4444-0000-0000-000000000001");
    private static final String KEY = "7c0b6a1e-key";

    /** Commits what the work wrote, or throws it away when the work throws (like a rollback). */
    private final class FakeStore implements UnitOfWork, ProductPublications, ProductRepository {
        final Map<UUID, Product> products = new LinkedHashMap<>();
        final Map<String, UUID> keys = new HashMap<>();
        private Map<UUID, Product> pendingProducts;
        private Map<String, UUID> pendingKeys;

        @Override
        public <T> T inTransaction(Supplier<T> work) {
            pendingProducts = new LinkedHashMap<>(products);
            pendingKeys = new HashMap<>(keys);
            try {
                T result = work.get();
                products.clear();
                products.putAll(pendingProducts);
                keys.clear();
                keys.putAll(pendingKeys);
                return result;
            } finally {
                pendingProducts = null;
                pendingKeys = null;
            }
        }

        @Override
        public void add(Product product) {
            assertThat(pendingProducts).as("add inside the unit of work").isNotNull();
            pendingProducts.put(product.id(), product);
        }

        @Override
        public boolean claimKey(UUID producerId, String idempotencyKey, UUID productId) {
            assertThat(pendingKeys).as("claim inside the unit of work").isNotNull();
            return pendingKeys.putIfAbsent(producerId + "/" + idempotencyKey, productId) == null;
        }

        @Override
        public Optional<UUID> productForKey(UUID producerId, String idempotencyKey) {
            return Optional.ofNullable(keys.get(producerId + "/" + idempotencyKey));
        }

        @Override
        public Page<Product> findPage(PageRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Product> findById(UUID id) {
            return Optional.ofNullable(products.get(id));
        }
    }

    private final FakeStore store = new FakeStore();
    private final PublishProductService service = new PublishProductService(store, store, store,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private static Command command(UUID producer, String key, String quantity, long priceCents) {
        return new Command(producer, "Ana Ruiz", key, "Café pergamino seco", ProductCategory.CAFE,
                ProductUnit.ARROBA, new BigDecimal(quantity), priceCents, "Garzón");
    }

    @Test
    void publishingStoresTheProductWithTheProducerOfTheToken() {
        Publication publication = service.publish(command(ANA, KEY, "40", 8_500_000));

        Product product = publication.product();
        assertThat(publication.created()).isTrue();
        assertThat(product.producerId()).isEqualTo(ANA);
        assertThat(product.producerName()).isEqualTo("Ana Ruiz");
        assertThat(product.price()).isEqualTo(Money.ofCents(8_500_000));
        assertThat(product.quantity()).isEqualTo(Quantity.of("40"));
        assertThat(product.status()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(product.photoUrl()).isNull();
        assertThat(product.createdAt()).isEqualTo(NOW);
        assertThat(store.products).containsKey(product.id());
        assertThat(store.keys).containsEntry(ANA + "/" + KEY, product.id());
    }

    @Test
    void zeroQuantityIsPublishedOutOfStock() {
        assertThat(service.publish(command(ANA, KEY, "0", 1)).product().status())
                .isEqualTo(ProductStatus.OUT_OF_STOCK);
    }

    @Test
    void replayAnswersTheOriginalAndKeepsOneProduct() {
        Product original = service.publish(command(ANA, KEY, "40", 8_500_000)).product();

        Publication replay = service.publish(command(ANA, KEY, "5", 1_000));

        assertThat(replay.created()).isFalse();
        assertThat(replay.product()).isSameAs(original);
        assertThat(store.products.size()).isEqualTo(1);
    }

    @Test
    void anotherProducerMayUseTheSameKey() {
        service.publish(command(ANA, KEY, "40", 8_500_000));

        assertThat(service.publish(command(UUID.randomUUID(), KEY, "40", 8_500_000)).created()).isTrue();
        assertThat(store.products.size()).isEqualTo(2);
    }

    @Test
    void priceIsCheckedBeforeQuantityAndNothingIsStored() {
        assertThatThrownBy(() -> service.publish(command(ANA, KEY, "-1", 0)))
                .isInstanceOfSatisfying(DomainException.class,
                        refused -> assertThat(refused.code()).isEqualTo("PRICE_NOT_POSITIVE"));
        assertThatThrownBy(() -> service.publish(command(ANA, KEY, "-1", 100)))
                .isInstanceOfSatisfying(DomainException.class,
                        refused -> assertThat(refused.code()).isEqualTo("QUANTITY_NEGATIVE"));
        assertThat(store.products).isEmpty();
    }

    @Test
    void invalidDataIsRefusedBeforeTheReplayCheck() {
        service.publish(command(ANA, KEY, "40", 8_500_000));

        assertThatThrownBy(() -> service.publish(command(ANA, KEY, "40", 0)))
                .isInstanceOfSatisfying(DomainException.class,
                        refused -> assertThat(refused.code()).isEqualTo("PRICE_NOT_POSITIVE"));
    }
}
