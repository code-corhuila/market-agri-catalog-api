package co.edu.corhuila.marketagri.catalog.domain.model;

/** A requested quantity of zero or less (I-03, {@code 422 QUANTITY_NOT_POSITIVE}). */
public final class QuantityNotPositiveException extends DomainException {

    public QuantityNotPositiveException(Object requested) {
        super("QUANTITY_NOT_POSITIVE", "quantity must be greater than zero: " + requested);
    }
}
