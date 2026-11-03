package co.edu.corhuila.marketagri.catalog.domain.model;

import java.time.Instant;
import java.util.Objects;
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
    private final String name;
    private final ProductCategory category;
    private final ProductUnit unit;
    private final Money price;
    private final String municipality;
    private final String photoUrl;
    private final Instant createdAt;
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

    /** The owner sets the available quantity (E-14, FR-013). */
    public void changeQuantity(Quantity newQuantity, Instant now) {
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
