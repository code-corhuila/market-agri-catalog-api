package co.edu.corhuila.marketagri.catalog.application.usecase;

import co.edu.corhuila.marketagri.catalog.application.port.in.BrowseProducts;
import co.edu.corhuila.marketagri.catalog.application.port.out.ProductRepository;
import co.edu.corhuila.marketagri.catalog.domain.model.Page;
import co.edu.corhuila.marketagri.catalog.domain.model.PageRequest;
import co.edu.corhuila.marketagri.catalog.domain.model.Product;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductNotFoundException;
import java.util.UUID;

/** E-10 and E-11: reading only, so no rule beyond "a deleted product does not exist". */
public final class BrowseProductsService implements BrowseProducts {

    private final ProductRepository products;

    public BrowseProductsService(ProductRepository products) {
        this.products = products;
    }

    @Override
    public Page<Product> list(PageRequest request) {
        return products.findPage(request);
    }

    @Override
    public Product get(UUID id) {
        return products.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }
}
