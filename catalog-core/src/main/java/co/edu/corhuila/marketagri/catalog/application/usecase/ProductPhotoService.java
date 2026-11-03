package co.edu.corhuila.marketagri.catalog.application.usecase;

import co.edu.corhuila.marketagri.catalog.application.port.in.ManageProductPhoto;
import co.edu.corhuila.marketagri.catalog.application.port.in.ServePhoto;
import co.edu.corhuila.marketagri.catalog.application.port.out.PhotoStorage;
import co.edu.corhuila.marketagri.catalog.application.port.out.ProductPhotos;
import co.edu.corhuila.marketagri.catalog.application.port.out.UnitOfWork;
import co.edu.corhuila.marketagri.catalog.domain.model.ImageFormat;
import co.edu.corhuila.marketagri.catalog.domain.model.PhotoKey;
import co.edu.corhuila.marketagri.catalog.domain.model.Product;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductNotFoundException;
import co.edu.corhuila.marketagri.catalog.domain.model.UnsupportedImageException;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * E-16, E-17 and E-62. The new binary is saved before the commit and the previous one deleted after
 * it: if anything fails in between, the product still points to a binary that exists (an orphan
 * binary is harmless, a key pointing to nothing is a permanent broken image).
 */
public final class ProductPhotoService implements ManageProductPhoto, ServePhoto {

    private final ProductPhotos products;
    private final PhotoStorage storage;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public ProductPhotoService(ProductPhotos products, PhotoStorage storage, UnitOfWork unitOfWork, Clock clock) {
        this.products = products;
        this.storage = storage;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    @Override
    public Product setPhoto(UUID productId, UUID producerId, byte[] content) {
        Change change = unitOfWork.inTransaction(() -> {
            Product product = ownedProduct(productId, producerId);
            ImageFormat format = ImageFormat.detect(content).orElseThrow(UnsupportedImageException::new);
            Optional<PhotoKey> previous = product.photo();
            PhotoKey key = PhotoKey.random(format);
            storage.save(key, content);
            product.changePhoto(key, clock.instant());
            products.updatePhoto(product);
            return new Change(product, previous);
        });
        change.previous().ifPresent(storage::delete);
        return change.product();
    }

    @Override
    public void removePhoto(UUID productId, UUID producerId) {
        Optional<PhotoKey> previous = unitOfWork.inTransaction(() -> {
            Product product = ownedProduct(productId, producerId);
            Optional<PhotoKey> photo = product.photo();
            if (photo.isPresent()) {
                product.removePhoto(clock.instant());
                products.updatePhoto(product);
            }
            return photo;
        });
        previous.ifPresent(storage::delete);
    }

    @Override
    public Optional<Photo> find(String key) {
        return PhotoKey.parse(key).flatMap(photoKey -> storage.load(photoKey).map(bytes -> new Photo(photoKey, bytes)));
    }

    private Product ownedProduct(UUID productId, UUID producerId) {
        Product product = products.findByIdForUpdate(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        product.ensureOwnedBy(producerId);
        return product;
    }

    private record Change(Product product, Optional<PhotoKey> previous) {
    }
}
