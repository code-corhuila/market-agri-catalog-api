package co.edu.corhuila.marketagri.catalog.domain.model;

import java.util.UUID;

/** A producer acting on another producer's product ({@code 403 NOT_PRODUCT_OWNER}). */
public final class NotProductOwnerException extends DomainException {

    public NotProductOwnerException(UUID productId, UUID producerId) {
        super("NOT_PRODUCT_OWNER", "product " + productId + " is not owned by " + producerId);
    }
}
