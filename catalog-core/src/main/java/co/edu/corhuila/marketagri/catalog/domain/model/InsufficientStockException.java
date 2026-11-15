package co.edu.corhuila.marketagri.catalog.domain.model;

/** A reservation larger than the stock (I-03, {@code 409}); the message of the contract names both numbers. */
public final class InsufficientStockException extends DomainException {

    private final Quantity available;
    private final Quantity requested;

    public InsufficientStockException(Quantity available, Quantity requested) {
        super("INSUFFICIENT_STOCK", "requested " + requested + ", available " + available);
        this.available = available;
        this.requested = requested;
    }

    public Quantity available() {
        return available;
    }

    public Quantity requested() {
        return requested;
    }
}
