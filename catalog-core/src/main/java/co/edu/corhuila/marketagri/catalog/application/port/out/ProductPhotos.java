package co.edu.corhuila.marketagri.catalog.application.port.out;

import co.edu.corhuila.marketagri.catalog.domain.model.Product;
import java.util.Optional;
import java.util.UUID;

/**
 * Products as a photo change needs them (E-16, E-17): read under a lock, so two changes of the same
 * photo never both delete the same previous binary, and the photo written back. Deleted products do
 * not exist here either (INV-007).
 */
public interface ProductPhotos {

    Optional<Product> findByIdForUpdate(UUID id);

    /** Stores {@code photoUrl} and {@code updatedAt}. */
    void updatePhoto(Product product);
}
