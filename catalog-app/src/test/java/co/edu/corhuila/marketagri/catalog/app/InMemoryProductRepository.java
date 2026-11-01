package co.edu.corhuila.marketagri.catalog.app;

import co.edu.corhuila.marketagri.catalog.application.port.out.ProductRepository;
import co.edu.corhuila.marketagri.catalog.domain.model.Page;
import co.edu.corhuila.marketagri.catalog.domain.model.PageRequest;
import co.edu.corhuila.marketagri.catalog.domain.model.Product;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The repository port in memory, for the HTTP tests (Anexo C): same order as E-10. */
final class InMemoryProductRepository implements ProductRepository {

    private static final Comparator<Product> E10_ORDER =
            Comparator.comparing(Product::createdAt).reversed().thenComparing(Product::id);

    private final List<Product> products = new ArrayList<>();

    void clear() {
        products.clear();
    }

    void add(Product product) {
        products.add(product);
    }

    @Override
    public Page<Product> findPage(PageRequest request) {
        List<Product> items = products.stream().sorted(E10_ORDER)
                .skip(request.offset()).limit(request.limit()).toList();
        return new Page<>(items, request, products.size());
    }

    @Override
    public Optional<Product> findById(UUID id) {
        return products.stream().filter(product -> product.id().equals(id)).findFirst();
    }
}
