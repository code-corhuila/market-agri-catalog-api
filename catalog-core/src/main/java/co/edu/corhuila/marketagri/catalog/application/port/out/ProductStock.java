package co.edu.corhuila.marketagri.catalog.application.port.out;

import co.edu.corhuila.marketagri.catalog.domain.model.Product;
import java.util.Optional;
import java.util.UUID;

/**
 * Products as a reservation needs them (I-03, I-04): read under a lock and written back. Apart
 * from {@link ProductRepository}, which only reads, so browsing never sees a lock. Deleted
 * products do not exist for this port either (INV-007).
 */
public interface ProductStock {

    /**
     * Holds the product until the {@link UnitOfWork} ends: a second reservation of the same product
     * waits here, so two of them never take the same stock.
     */
    Optional<Product> findByIdForUpdate(UUID id);

    /** Stores what a reservation or a release changes: quantity, status and {@code updatedAt}. */
    void updateStock(Product product);
}
