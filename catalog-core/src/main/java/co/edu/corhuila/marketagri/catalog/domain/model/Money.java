package co.edu.corhuila.marketagri.catalog.domain.model;

/**
 * A price in integer centavos (ADR-029): {@code 8500000} is {@code $ 85.000}. Always greater than
 * zero (INV-005). The currency is always COP, so it is not stored.
 */
public record Money(long cents) {

    public Money {
        if (cents <= 0) {
            throw new DomainException("PRICE_NOT_POSITIVE", "price must be greater than zero: " + cents);
        }
    }

    public static Money ofCents(long cents) {
        return new Money(cents);
    }
}
