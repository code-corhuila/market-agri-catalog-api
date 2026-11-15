package co.edu.corhuila.marketagri.catalog.application.port.out;

import co.edu.corhuila.marketagri.catalog.domain.model.Product;
import java.util.Optional;
import java.util.UUID;

/**
 * Products as the owner's edit needs them (E-14): read under a lock, so an edit and a reservation of
 * the same product never overwrite each other's quantity, and written back. Deleted products do not
 * exist here either (INV-007).
 */
public interface ProductEdits {

    Optional<Product> findByIdForUpdate(UUID id);

    /** Stores what an edit changes: name, category, unit, quantity, status, price, municipality and its key. */
    void updateDetails(Product product);
}
