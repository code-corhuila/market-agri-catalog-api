package co.edu.corhuila.marketagri.catalog.application.port.in;

import co.edu.corhuila.marketagri.catalog.domain.model.Page;
import co.edu.corhuila.marketagri.catalog.domain.model.PageRequest;
import co.edu.corhuila.marketagri.catalog.domain.model.Product;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductFilter;
import java.util.UUID;

/** Anyone browses the catalog: E-10 list with its filters (HU-CAT-005, HU-CAT-006) and E-11 detail. */
public interface BrowseProducts {

    Page<Product> list(ProductFilter filter, PageRequest request);

    /** The product, or {@code ProductNotFoundException}. */
    Product get(UUID id);
}
