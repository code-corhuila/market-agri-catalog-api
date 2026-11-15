package co.edu.corhuila.marketagri.catalog.application.port.out;

import co.edu.corhuila.marketagri.catalog.domain.model.Page;
import co.edu.corhuila.marketagri.catalog.domain.model.PageRequest;
import co.edu.corhuila.marketagri.catalog.domain.model.Product;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductFilter;
import java.util.Optional;
import java.util.UUID;

/** Products as the core needs them. Deleted products do not exist for this port (INV-007). */
public interface ProductRepository {

    /** The products that match the filter, newest first, then by id (the order of E-10). */
    Page<Product> findPage(ProductFilter filter, PageRequest request);

    Optional<Product> findById(UUID id);
}
