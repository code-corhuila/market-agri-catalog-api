package co.edu.corhuila.marketagri.catalog.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Aggregate root of the catalog: what a producer offers, with the stock available now (D-C12).
 *
 * <p>INV-009: the status is never set from outside. {@link #applyQuantity} is the only place that
 * changes the quantity, and it derives the status every time — on creation, owner edit,
 * reservation and release. The database enforces the same rule with two CHECKs
 * (catalog-db A3 and A4).
 */
public final class Product {

    private final UUID id;
    private final UUID producerId;
    private final String producerName;
    private final Instant createdAt;
    private String name;
    private ProductCategory category;
    private ProductUnit unit;
    private Money price;
    private String municipality;
    private String photoUrl;
    private Quantity quantity;
    private ProductStatus status;
    private Instant updatedAt;

    private Product(UUID id, UUID producerId, String producerName, String name, ProductCategory category,
                    ProductUnit unit, Money price, String municipality, String photoUrl, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.producerId = Objects.requireNonNull(producerId, "producerId");
        this.producerName = Objects.requireNonNull(producerName, "producerName");
        this.name = Objects.requireNonNull(name, "name");
        this.category = Objects.requireNonNull(category, "category");
        this.unit = Objects.requireNonNull(unit, "unit");
        this.price = Objects.requireNonNull(price, "price");
        this.municipality = Objects.requireNonNull(municipality, "municipality");
        this.photoUrl = photoUrl;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /**
     * A new product (E-13): no photo yet; the status comes from the quantity. Name and
     * municipality are stored trimmed, and the municipality must have a {@link MunicipalityKey}.
     */
    public static Product create(UUID id, UUID producerId, String producerName, String name,
                                 ProductCategory category, ProductUnit unit, Quantity quantity, Money price,
                                 String municipality, Instant now) {
        MunicipalityKey.of(municipality);
        Product product = new Product(id, producerId, producerName, name.strip(), category, unit, price,
                municipality.strip(), null, now);
        product.applyQuantity(quantity, now);
        return product;
    }

    /** A stored product, read back by the persistence adapter: the status is derived again, not read. */
    public static Product restore(UUID id, UUID producerId, String producerName, String name,
                                  ProductCategory category, ProductUnit unit, Quantity quantity, Money price,
                                  String municipality, String photoUrl, Instant createdAt, Instant updatedAt) {
        Product product = new Product(id, producerId, producerName, name, category, unit, price, municipality,
                photoUrl, createdAt);
        product.applyQuantity(quantity, updatedAt);
        return product;
    }

    /**
     * The owner replaces what a producer edits (E-14, full replacement), trimmed as in
     * {@link #create}; the status follows the new quantity (INV-009, FR-013). Who published it, when,
     * and the photo stay: the identity of the product does not change.
     */
    public void edit(String newName, ProductCategory newCategory, ProductUnit newUnit, Quantity newQuantity,
                     Money newPrice, String newMunicipality, Instant now) {
        MunicipalityKey.of(newMunicipality);
        this.name = Objects.requireNonNull(newName, "newName").strip();
        this.category = Objects.requireNonNull(newCategory, "newCategory");
        this.unit = Objects.requireNonNull(newUnit, "newUnit");
        this.price = Objects.requireNonNull(newPrice, "newPrice");
        this.municipality = newMunicipality.strip();
        applyQuantity(Objects.requireNonNull(newQuantity, "newQuantity"), now);
    }

    /**
     * Holds stock for a payment (I-03): only if it is all available; otherwise nothing changes. The
     * checks follow the order of the contract: not positive, not available, insufficient.
     */
    public void reserve(Quantity requested, Instant now) {
        if (requested.isZero()) {
            throw new QuantityNotPositiveException(requested);
        }
        if (status == ProductStatus.OUT_OF_STOCK) {
            throw new ProductNotAvailableException(id);
        }
        if (requested.isGreaterThan(quantity)) {
            throw new InsufficientStockException(quantity, requested);
        }
        applyQuantity(quantity.minus(requested), now);
    }

    /** Only its producer may change it (E-14, E-16, E-17, E-18): otherwise {@code NOT_PRODUCT_OWNER}. */
    public void ensureOwnedBy(UUID actingProducerId) {
        if (!producerId.equals(actingProducerId)) {
            throw new NotProductOwnerException(id, actingProducerId);
        }
    }

    /** Sets or replaces the photo (E-16, D-C8: one photo per product). */
    public void changePhoto(PhotoKey newPhoto, Instant now) {
        this.photoUrl = Objects.requireNonNull(newPhoto, "newPhoto").url();
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    /** Leaves the product without a photo (E-17). */
    public void removePhoto(Instant now) {
        this.photoUrl = null;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    /** Gives back the stock of a reservation (I-04 or the saga's compensation). */
    public void release(Quantity reserved, Instant now) {
        applyQuantity(quantity.plus(reserved), now);
    }

    private void applyQuantity(Quantity newQuantity, Instant now) {
        this.quantity = newQuantity;
        this.status = ProductStatus.of(newQuantity);
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public UUID id() {
        return id;
    }

    public UUID producerId() {
        return producerId;
    }

    public String producerName() {
        return producerName;
    }

    public String name() {
        return name;
    }

    public ProductCategory category() {
        return category;
    }

    public ProductUnit unit() {
        return unit;
    }

    public Quantity quantity() {
        return quantity;
    }

    public Money price() {
        return price;
    }

    public String municipality() {
        return municipality;
    }

    /** What the filter of E-10 compares (D-16). */
    public MunicipalityKey municipalityKey() {
        return MunicipalityKey.of(municipality);
    }

    /**
     * The key of the stored binary, read from {@code photoUrl}. Empty without a photo, and also for a
     * {@code /media/} URL that is not a key: that binary is then never deleted, an orphan at worst.
     */
    public Optional<PhotoKey> photo() {
        return PhotoKey.fromUrl(photoUrl);
    }

    /** {@code /media/{key}}, or {@code null} without a photo: never {@code ""} (D-C8). */
    public String photoUrl() {
        return photoUrl;
    }

    public ProductStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
