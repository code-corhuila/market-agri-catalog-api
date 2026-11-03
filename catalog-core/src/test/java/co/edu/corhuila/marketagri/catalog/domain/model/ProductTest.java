package co.edu.corhuila.marketagri.catalog.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * INV-009 (status follows quantity, FR-013): the status is derived in one method of the aggregate
 * every time the quantity changes — creation, owner edit, reservation and release — and never set
 * from outside.
 */
class ProductTest {

    private static final Instant CREATED = Instant.parse("2026-11-01T15:00:00Z");
    private static final Instant LATER = Instant.parse("2026-11-01T16:00:00Z");

    private static Quantity quantity(String value) {
        return Quantity.of(value);
    }

    private static Product created(String quantity) {
        return Product.create(UUID.randomUUID(), UUID.randomUUID(), "Ana Ruiz", "Café pergamino seco",
                ProductCategory.CAFE, ProductUnit.ARROBA, quantity(quantity), Money.ofCents(8_500_000),
                "Garzón", CREATED);
    }

    @Test
    void createdWithStockIsActive() {
        Product product = created("40");

        assertThat(product.status()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(product.createdAt()).isEqualTo(CREATED);
        assertThat(product.updatedAt()).isEqualTo(CREATED);
    }

    @Test
    void createdTrimsNameAndMunicipalityAndKeysTheMunicipality() {
        Product product = Product.create(UUID.randomUUID(), UUID.randomUUID(), "Ana Ruiz", "  Café  ",
                ProductCategory.CAFE, ProductUnit.ARROBA, quantity("1"), Money.ofCents(1), " San Agustín ",
                CREATED);

        assertThat(product.name()).isEqualTo("Café");
        assertThat(product.municipality()).isEqualTo("San Agustín");
        assertThat(product.municipalityKey()).isEqualTo(new MunicipalityKey("san agustin"));
    }

    @Test
    void createdWithoutStockIsOutOfStock() {
        assertThat(created("0").status()).isEqualTo(ProductStatus.OUT_OF_STOCK);
    }

    @Test
    void restoredProductDerivesItsStatusFromTheQuantity() {
        Product withStock = Product.restore(UUID.randomUUID(), UUID.randomUUID(), "Ana Ruiz", "Café",
                ProductCategory.CAFE, ProductUnit.ARROBA, quantity("12.5"), Money.ofCents(680_000),
                "Garzón", null, CREATED, LATER);
        Product withoutStock = Product.restore(UUID.randomUUID(), UUID.randomUUID(), "Ana Ruiz", "Café",
                ProductCategory.CAFE, ProductUnit.ARROBA, quantity("0"), Money.ofCents(680_000),
                "Garzón", "/media/photo.jpg", CREATED, LATER);

        assertThat(withStock.status()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(withoutStock.status()).isEqualTo(ProductStatus.OUT_OF_STOCK);
        assertThat(withoutStock.photoUrl()).isEqualTo("/media/photo.jpg");
    }

    @Test
    void ownerEditToZeroMakesItOutOfStock() {
        Product product = created("40");

        product.changeQuantity(quantity("0"), LATER);

        assertThat(product.status()).isEqualTo(ProductStatus.OUT_OF_STOCK);
        assertThat(product.updatedAt()).isEqualTo(LATER);
    }

    @Test
    void ownerEditFromZeroMakesItActive() {
        Product product = created("0");

        product.changeQuantity(quantity("5"), LATER);

        assertThat(product.status()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(product.quantity()).isEqualTo(quantity("5.00"));
    }

    @Test
    void reservingTheLastUnitsMakesItOutOfStock() {
        Product product = created("12.5");

        product.reserve(quantity("12.5"), LATER);

        assertThat(product.quantity()).isEqualTo(quantity("0"));
        assertThat(product.status()).isEqualTo(ProductStatus.OUT_OF_STOCK);
        assertThat(product.updatedAt()).isEqualTo(LATER);
    }

    @Test
    void reservingPartOfTheStockKeepsItActive() {
        Product product = created("40");

        product.reserve(quantity("2"), LATER);

        assertThat(product.quantity()).isEqualTo(quantity("38"));
        assertThat(product.status()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    void reservingMoreThanAvailableIsRefusedAndChangesNothing() {
        Product product = created("3");

        assertThatThrownBy(() -> product.reserve(quantity("5"), LATER))
                .isInstanceOfSatisfying(InsufficientStockException.class, refused -> {
                    assertThat(refused.code()).isEqualTo("INSUFFICIENT_STOCK");
                    assertThat(refused.available()).isEqualTo(quantity("3"));
                    assertThat(refused.requested()).isEqualTo(quantity("5"));
                });
        assertThat(product.quantity()).isEqualTo(quantity("3"));
        assertThat(product.status()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(product.updatedAt()).isEqualTo(CREATED);
    }

    @Test
    void reservingNothingIsRefused() {
        Product product = created("3");

        assertThatThrownBy(() -> product.reserve(quantity("0"), LATER))
                .isInstanceOfSatisfying(QuantityNotPositiveException.class,
                        refused -> assertThat(refused.code()).isEqualTo("QUANTITY_NOT_POSITIVE"));
    }

    @Test
    void reservingAProductOutOfStockIsNotAvailableBeforeInsufficient() {
        Product product = created("0");

        assertThatThrownBy(() -> product.reserve(quantity("1"), LATER))
                .isInstanceOfSatisfying(ProductNotAvailableException.class,
                        refused -> assertThat(refused.code()).isEqualTo("PRODUCT_NOT_AVAILABLE"));
        assertThat(product.updatedAt()).isEqualTo(CREATED);
    }

    @Test
    void releasingAReservationMakesItActiveAgain() {
        Product product = created("2");
        product.reserve(quantity("2"), CREATED);

        product.release(quantity("2"), LATER);

        assertThat(product.quantity()).isEqualTo(quantity("2"));
        assertThat(product.status()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(product.updatedAt()).isEqualTo(LATER);
    }

    @Test
    void onlyItsProducerOwnsTheProduct() {
        UUID ana = UUID.randomUUID();
        Product product = Product.create(UUID.randomUUID(), ana, "Ana Ruiz", "Café", ProductCategory.CAFE,
                ProductUnit.ARROBA, quantity("1"), Money.ofCents(1), "Garzón", CREATED);

        product.ensureOwnedBy(ana);
        assertThatThrownBy(() -> product.ensureOwnedBy(UUID.randomUUID()))
                .isInstanceOfSatisfying(NotProductOwnerException.class,
                        refused -> assertThat(refused.code()).isEqualTo("NOT_PRODUCT_OWNER"));
    }

    @Test
    void aNewProductHasNoPhoto() {
        Product product = created("1");

        assertThat(product.photo()).isEqualTo(Optional.empty());
        assertThat(product.photoUrl()).isNull();
    }

    @Test
    void changingThePhotoKeepsItsKeyAsTheUrl() {
        Product product = created("1");
        PhotoKey key = PhotoKey.random(ImageFormat.PNG);

        product.changePhoto(key, LATER);

        assertThat(product.photo()).isEqualTo(Optional.of(key));
        assertThat(product.photoUrl()).isEqualTo("/media/" + key.value());
        assertThat(product.updatedAt()).isEqualTo(LATER);
        assertThat(product.status()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    void removingThePhotoLeavesNone() {
        Product product = created("1");
        product.changePhoto(PhotoKey.random(ImageFormat.JPEG), CREATED);

        product.removePhoto(LATER);

        assertThat(product.photoUrl()).isNull();
        assertThat(product.updatedAt()).isEqualTo(LATER);
    }

    @Test
    void aStoredPhotoUrlIsReadBackAsItsKey() {
        String url = "/media/0123456789abcdef0123456789abcdef.webp";
        Product product = Product.restore(UUID.randomUUID(), UUID.randomUUID(), "Ana Ruiz", "Café",
                ProductCategory.CAFE, ProductUnit.ARROBA, quantity("1"), Money.ofCents(1), "Garzón", url,
                CREATED, LATER);

        assertThat(product.photoUrl()).isEqualTo(url);
        assertThat(product.photo().orElseThrow().format()).isEqualTo(ImageFormat.WEBP);
    }
}
