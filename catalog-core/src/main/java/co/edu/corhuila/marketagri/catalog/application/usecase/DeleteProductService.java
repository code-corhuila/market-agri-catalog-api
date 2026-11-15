package co.edu.corhuila.marketagri.catalog.application.usecase;

import co.edu.corhuila.marketagri.catalog.application.port.in.DeleteProduct;
import co.edu.corhuila.marketagri.catalog.application.port.out.PhotoStorage;
import co.edu.corhuila.marketagri.catalog.application.port.out.ProductRemovals;
import co.edu.corhuila.marketagri.catalog.application.port.out.UnitOfWork;
import co.edu.corhuila.marketagri.catalog.domain.model.PhotoKey;
import co.edu.corhuila.marketagri.catalog.domain.model.Product;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductNotFoundException;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * E-18. The product is marked as deleted inside the unit of work and its photo binary is deleted
 * after the commit, as in E-17: if the commit fails, the product is still alive and its photo still
 * exists. Pending reservations stay; their release finds no product and only marks itself (I-04).
 */
public final class DeleteProductService implements DeleteProduct {

    private final ProductRemovals products;
    private final PhotoStorage storage;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public DeleteProductService(ProductRemovals products, PhotoStorage storage, UnitOfWork unitOfWork,
                                Clock clock) {
        this.products = products;
        this.storage = storage;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    @Override
    public void delete(UUID productId, UUID producerId) {
        Optional<PhotoKey> photo = unitOfWork.inTransaction(() -> {
            Product product = products.findByIdForUpdate(productId)
                    .orElseThrow(() -> new ProductNotFoundException(productId));
            product.ensureOwnedBy(producerId);
            products.markDeleted(productId, clock.instant());
            return product.photo();
        });
        photo.ifPresent(storage::delete);
    }
}
