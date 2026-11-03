package co.edu.corhuila.marketagri.catalog.application.port.out;

import co.edu.corhuila.marketagri.catalog.domain.model.Product;
import java.util.Optional;
import java.util.UUID;

/** Where a new product and its {@code Idempotency-Key} are stored, in one {@link UnitOfWork} (D-C35). */
public interface ProductPublications {

    void add(Product product);

    /** False when the producer already used the key: nothing is written. */
    boolean claimKey(UUID producerId, String idempotencyKey, UUID productId);

    /** The product created earlier with this key, if any. */
    Optional<UUID> productForKey(UUID producerId, String idempotencyKey);
}
