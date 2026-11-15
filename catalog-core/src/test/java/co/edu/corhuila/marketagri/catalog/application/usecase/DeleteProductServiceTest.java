package co.edu.corhuila.marketagri.catalog.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.marketagri.catalog.application.port.out.PhotoStorage;
import co.edu.corhuila.marketagri.catalog.application.port.out.ProductRemovals;
import co.edu.corhuila.marketagri.catalog.application.port.out.UnitOfWork;
import co.edu.corhuila.marketagri.catalog.domain.model.ImageFormat;
import co.edu.corhuila.marketagri.catalog.domain.model.Money;
import co.edu.corhuila.marketagri.catalog.domain.model.NotProductOwnerException;
import co.edu.corhuila.marketagri.catalog.domain.model.PhotoKey;
import co.edu.corhuila.marketagri.catalog.domain.model.Product;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductCategory;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductNotFoundException;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductUnit;
import co.edu.corhuila.marketagri.catalog.domain.model.Quantity;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** E-18 in the core: marked inside the unit of work, the photo binary deleted only after it. */
class DeleteProductServiceTest {

    private static final Instant CREATED = Instant.parse("2026-11-01T15:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-04T15:00:00Z");
    private static final UUID ANA = UUID.randomUUID();
    private static final UUID LUIS = UUID.randomUUID();
    private static final PhotoKey PHOTO = PhotoKey.random(ImageFormat.JPEG);

    /** The products, and what happened in which order: "mark", "commit", "delete-binary". */
    private final List<String> events = new ArrayList<>();

    private final class FakeStore implements ProductRemovals, UnitOfWork {
        final Map<UUID, Product> alive = new HashMap<>();
        boolean inside;
        boolean failOnMark;
        Instant markedAt;

        @Override
        public Optional<Product> findByIdForUpdate(UUID id) {
            assertThat(inside).as("read under the lock, inside the unit of work").isTrue();
            return Optional.ofNullable(alive.get(id));
        }

        @Override
        public void markDeleted(UUID id, Instant now) {
            assertThat(inside).as("marked inside the unit of work").isTrue();
            if (failOnMark) {
                throw new IllegalStateException("database down");
            }
            alive.remove(id);
            markedAt = now;
            events.add("mark");
        }

        @Override
        public <T> T inTransaction(Supplier<T> work) {
            inside = true;
            try {
                T result = work.get();
                events.add("commit");
                return result;
            } finally {
                inside = false;
            }
        }
    }

    private final class FakeStorage implements PhotoStorage {
        @Override
        public void save(PhotoKey key, byte[] content) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<byte[]> load(PhotoKey key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(PhotoKey key) {
            assertThat(key).isEqualTo(PHOTO);
            events.add("delete-binary");
        }
    }

    private final FakeStore store = new FakeStore();
    private final DeleteProductService service = new DeleteProductService(store, new FakeStorage(), store,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private Product stored(String photoUrl) {
        Product product = Product.restore(UUID.randomUUID(), ANA, "Ana Ruiz", "Café pergamino seco",
                ProductCategory.CAFE, ProductUnit.ARROBA, Quantity.of("40"), Money.ofCents(8_500_000), "Garzón",
                photoUrl, CREATED, CREATED);
        store.alive.put(product.id(), product);
        return product;
    }

    @Test
    void ownerDeletesAndThePhotoBinaryGoesAfterTheCommit() {
        Product product = stored(PHOTO.url());

        service.delete(product.id(), ANA);

        assertThat(store.alive).isEmpty();
        assertThat(store.markedAt).isEqualTo(NOW);
        assertThat(events).containsExactly("mark", "commit", "delete-binary");
    }

    @Test
    void withoutPhotoNoBinaryIsDeleted() {
        Product product = stored(null);

        service.delete(product.id(), ANA);

        assertThat(events).containsExactly("mark", "commit");
    }

    @Test
    void secondDeletionIsNotFound() {
        Product product = stored(null);
        service.delete(product.id(), ANA);

        assertThatThrownBy(() -> service.delete(product.id(), ANA)).isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void someoneElsesProductIsNotProductOwnerAndStays() {
        Product product = stored(PHOTO.url());

        assertThatThrownBy(() -> service.delete(product.id(), LUIS)).isInstanceOf(NotProductOwnerException.class);

        assertThat(store.alive).containsKey(product.id());
        assertThat(events).isEmpty();
    }

    @Test
    void failedCommitKeepsThePhotoBinary() {
        Product product = stored(PHOTO.url());
        store.failOnMark = true;

        assertThatThrownBy(() -> service.delete(product.id(), ANA)).isInstanceOf(IllegalStateException.class);

        assertThat(events).doesNotContain("delete-binary");
    }
}
