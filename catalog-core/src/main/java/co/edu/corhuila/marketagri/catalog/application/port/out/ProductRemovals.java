package co.edu.corhuila.marketagri.catalog.application.port.out;

import co.edu.corhuila.marketagri.catalog.domain.model.Product;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Products as a deletion needs them (E-18): read under a lock, so two deletions of the same product
 * are ordered and the second one finds nothing, and marked as deleted. After that the product does
 * not exist for any port (INV-007): the row stays, for history.
 */
public interface ProductRemovals {

    Optional<Product> findByIdForUpdate(UUID id);

    /** Sets {@code deleted_at}; never removes the row. */
    void markDeleted(UUID id, Instant now);
}
