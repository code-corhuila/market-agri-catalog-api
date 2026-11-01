package co.edu.corhuila.marketagri.catalog.domain.model;

/**
 * A broken business rule, with the code the contract answers for it (api-contract §4.2:
 * {@code PRICE_NOT_POSITIVE}, {@code QUANTITY_NEGATIVE}, {@code INSUFFICIENT_STOCK}). The HTTP
 * adapter turns the code into the status and the Spanish message; the message here is for logs.
 */
public class DomainException extends RuntimeException {

    private final String code;

    public DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
