package co.edu.corhuila.marketagri.catalog.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.marketagri.catalog.application.port.in.ServePhoto;
import co.edu.corhuila.marketagri.catalog.application.port.out.PhotoStorage;
import co.edu.corhuila.marketagri.catalog.application.port.out.ProductPhotos;
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
import co.edu.corhuila.marketagri.catalog.domain.model.UnsupportedImageException;
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

/**
 * E-16, E-17 and E-62 in the core (HU-CAT-001 AC5–AC6, D-C8, D-C30): errors in the order of the
 * contract (404, 403, then 415), the photo key written in one unit of work, and the previous binary
 * deleted only after the commit — an orphan binary is harmless, a key pointing to nothing is not.
 */
class ProductPhotoServiceTest {

    private static final Instant CREATED = Instant.parse("2026-11-01T15:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z");
    private static final UUID ANA = UUID.fromString("0b6f2c7e-4a51-4c1e-9d0e-2f1c3a7b9e10");
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 4, 5};
    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-', '1', '.', '7'};

    /** What happened, in order: "save <key>", "commit", "rollback", "delete <key>". */
    private final List<String> events = new ArrayList<>();

    /** Commits the photo the work wrote, or throws it away when the work throws (like a rollback). */
    private final class FakeStore implements UnitOfWork, ProductPhotos {
        final Map<UUID, Product> products = new HashMap<>();
        final Map<UUID, String> committedUrls = new HashMap<>();
        private Map<UUID, String> pendingUrls;
        boolean failOnUpdate;

        @Override
        public <T> T inTransaction(Supplier<T> work) {
            pendingUrls = new HashMap<>(committedUrls);
            try {
                T result = work.get();
                committedUrls.clear();
                committedUrls.putAll(pendingUrls);
                events.add("commit");
                return result;
            } catch (RuntimeException failure) {
                events.add("rollback");
                throw failure;
            } finally {
                pendingUrls = null;
            }
        }

        @Override
        public Optional<Product> findByIdForUpdate(UUID id) {
            assertThat(pendingUrls).as("read inside the unit of work").isNotNull();
            return Optional.ofNullable(products.get(id));
        }

        @Override
        public void updatePhoto(Product product) {
            assertThat(pendingUrls).as("write inside the unit of work").isNotNull();
            if (failOnUpdate) {
                throw new IllegalStateException("database down");
            }
            pendingUrls.put(product.id(), product.photoUrl());
        }
    }

    private final class FakeStorage implements PhotoStorage {
        final Map<String, byte[]> files = new HashMap<>();

        @Override
        public void save(PhotoKey key, byte[] content) {
            events.add("save " + key.value());
            files.put(key.value(), content.clone());
        }

        @Override
        public Optional<byte[]> load(PhotoKey key) {
            return Optional.ofNullable(files.get(key.value()));
        }

        @Override
        public void delete(PhotoKey key) {
            events.add("delete " + key.value());
            files.remove(key.value());
        }
    }

    private final FakeStore store = new FakeStore();
    private final FakeStorage storage = new FakeStorage();
    private final ProductPhotoService service = new ProductPhotoService(store, storage, store,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private Product stored() {
        Product product = Product.create(UUID.randomUUID(), ANA, "Ana Ruiz", "Café pergamino seco",
                ProductCategory.CAFE, ProductUnit.ARROBA, Quantity.of("40"), Money.ofCents(8_500_000),
                "Garzón", CREATED);
        store.products.put(product.id(), product);
        return product;
    }

    @Test
    void thePhotoIsStoredAndItsKeyCommitted() {
        Product cafe = stored();

        Product changed = service.setPhoto(cafe.id(), ANA, PNG);

        String key = changed.photo().orElseThrow().value();
        assertThat(key).matches("[0-9a-f]{32}\\.png");
        assertThat(changed.photoUrl()).isEqualTo("/media/" + key);
        assertThat(changed.updatedAt()).isEqualTo(NOW);
        assertThat(store.committedUrls.get(cafe.id())).isEqualTo("/media/" + key);
        assertThat(storage.files.get(key)).isEqualTo(PNG);
        assertThat(events).containsExactly("save " + key, "commit");
    }

    @Test
    void replacingThePhotoDeletesThePreviousBinaryAfterTheCommit() {
        Product cafe = stored();
        String first = service.setPhoto(cafe.id(), ANA, PNG).photo().orElseThrow().value();
        events.clear();

        String second = service.setPhoto(cafe.id(), ANA, JPEG).photo().orElseThrow().value();

        assertThat(second).matches("[0-9a-f]{32}\\.jpg");
        assertThat(events).containsExactly("save " + second, "commit", "delete " + first);
        assertThat(storage.files.keySet()).containsExactly(second);
    }

    @Test
    void whenTheCommitFailsThePreviousPhotoStays() {
        Product cafe = stored();
        String first = service.setPhoto(cafe.id(), ANA, PNG).photo().orElseThrow().value();
        store.failOnUpdate = true;

        assertThatThrownBy(() -> service.setPhoto(cafe.id(), ANA, JPEG)).isInstanceOf(IllegalStateException.class);

        assertThat(store.committedUrls.get(cafe.id())).isEqualTo("/media/" + first);
        assertThat(storage.files.containsKey(first)).isTrue();
        assertThat(events).doesNotContain("delete " + first);
    }

    @Test
    void unknownProductIsNotFoundBeforeAnythingElse() {
        assertThatThrownBy(() -> service.setPhoto(UUID.randomUUID(), UUID.randomUUID(), PDF))
                .isInstanceOf(ProductNotFoundException.class);
        assertThat(storage.files).isEmpty();
    }

    @Test
    void anotherProducerIsForbiddenBeforeTheFormatIsChecked() {
        Product cafe = stored();

        assertThatThrownBy(() -> service.setPhoto(cafe.id(), UUID.randomUUID(), PDF))
                .isInstanceOf(NotProductOwnerException.class);
        assertThat(storage.files).isEmpty();
    }

    @Test
    void aFileThatIsNotJpgPngOrWebpIsRefusedAndNothingIsStored() {
        Product cafe = stored();

        assertThatThrownBy(() -> service.setPhoto(cafe.id(), ANA, PDF))
                .isInstanceOfSatisfying(UnsupportedImageException.class,
                        refused -> assertThat(refused.code()).isEqualTo("UNSUPPORTED_MEDIA_TYPE"));
        assertThat(storage.files).isEmpty();
        assertThat(cafe.photoUrl()).isNull();
    }

    @Test
    void removingClearsTheKeyAndThenDeletesTheBinary() {
        Product cafe = stored();
        String key = service.setPhoto(cafe.id(), ANA, PNG).photo().orElseThrow().value();
        events.clear();

        service.removePhoto(cafe.id(), ANA);

        assertThat(cafe.photoUrl()).isNull();
        assertThat(store.committedUrls.get(cafe.id())).isNull();
        assertThat(events).containsExactly("commit", "delete " + key);
    }

    @Test
    void removingWhenThereIsNoPhotoDeletesNothing() {
        Product cafe = stored();

        service.removePhoto(cafe.id(), ANA);

        assertThat(events).containsExactly("commit");
    }

    @Test
    void removingFollowsTheSameOrderOfErrors() {
        Product cafe = stored();

        assertThatThrownBy(() -> service.removePhoto(UUID.randomUUID(), ANA))
                .isInstanceOf(ProductNotFoundException.class);
        assertThatThrownBy(() -> service.removePhoto(cafe.id(), UUID.randomUUID()))
                .isInstanceOf(NotProductOwnerException.class);
    }

    @Test
    void aStoredPhotoIsServedByItsKeyWithItsFormat() {
        Product cafe = stored();
        String key = service.setPhoto(cafe.id(), ANA, PNG).photo().orElseThrow().value();

        ServePhoto.Photo photo = service.find(key).orElseThrow();

        assertThat(photo.content()).isEqualTo(PNG);
        assertThat(photo.mediaType()).isEqualTo(ImageFormat.PNG.mediaType());
    }

    @Test
    void anUnknownOrMalformedKeyServesNothing() {
        assertThat(service.find("0123456789abcdef0123456789abcdef.png")).isEqualTo(Optional.empty());
        assertThat(service.find("../catalog.png")).isEqualTo(Optional.empty());
    }
}
