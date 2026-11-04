package co.edu.corhuila.marketagri.catalog.app;

import co.edu.corhuila.marketagri.catalog.application.port.out.ProductEdits;
import co.edu.corhuila.marketagri.catalog.application.port.out.ProductPhotos;
import co.edu.corhuila.marketagri.catalog.application.port.out.ProductRemovals;
import co.edu.corhuila.marketagri.catalog.application.port.out.ProductPublications;
import co.edu.corhuila.marketagri.catalog.application.port.out.ProductRepository;
import co.edu.corhuila.marketagri.catalog.application.port.out.ProductStock;
import co.edu.corhuila.marketagri.catalog.domain.model.Page;
import co.edu.corhuila.marketagri.catalog.domain.model.PageRequest;
import co.edu.corhuila.marketagri.catalog.domain.model.Product;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductFilter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** The product ports in memory, for the HTTP tests (Anexo C): same order and filters as E-10. */
final class InMemoryProductRepository implements ProductRepository, ProductStock, ProductPublications,
        ProductPhotos, ProductEdits, ProductRemovals {

    private static final Comparator<Product> E10_ORDER =
            Comparator.comparing(Product::createdAt).reversed().thenComparing(Product::id);

    private final List<Product> products = new ArrayList<>();
    private final Map<String, UUID> keys = new HashMap<>();

    void clear() {
        products.clear();
        keys.clear();
    }

    @Override
    public void add(Product product) {
        products.add(product);
    }

    /** No transaction here: a used key removes the product just added, as the rollback would. */
    @Override
    public boolean claimKey(UUID producerId, String idempotencyKey, UUID productId) {
        if (keys.putIfAbsent(producerId + "/" + idempotencyKey, productId) == null) {
            return true;
        }
        products.removeIf(product -> product.id().equals(productId));
        return false;
    }

    @Override
    public Optional<UUID> productForKey(UUID producerId, String idempotencyKey) {
        return Optional.ofNullable(keys.get(producerId + "/" + idempotencyKey));
    }

    @Override
    public Page<Product> findPage(ProductFilter filter, PageRequest request) {
        List<Product> matching = products.stream().filter(filter::matches).sorted(E10_ORDER).toList();
        List<Product> items = matching.stream().skip(request.offset()).limit(request.limit()).toList();
        return new Page<>(items, request, matching.size());
    }

    @Override
    public Optional<Product> findById(UUID id) {
        return products.stream().filter(product -> product.id().equals(id)).findFirst();
    }

    /** One thread per HTTP test: nothing to lock. */
    @Override
    public Optional<Product> findByIdForUpdate(UUID id) {
        return findById(id);
    }

    /** The product in the list is the same object the core changed. */
    @Override
    public void updateStock(Product product) {
    }

    /** As {@link #updateStock}: the core already changed the product in the list. */
    @Override
    public void updatePhoto(Product product) {
    }

    /** As {@link #updateStock}: the core already changed the product in the list. */
    @Override
    public void updateDetails(Product product) {
    }

    /** A deleted product does not exist for any port (INV-007): out of the list. */
    @Override
    public void markDeleted(UUID id, Instant now) {
        products.removeIf(product -> product.id().equals(id));
    }
}
