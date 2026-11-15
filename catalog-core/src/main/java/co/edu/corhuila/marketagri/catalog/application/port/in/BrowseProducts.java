package co.edu.corhuila.marketagri.catalog.application.port.in;

import co.edu.corhuila.marketagri.catalog.domain.model.Page;
import co.edu.corhuila.marketagri.catalog.domain.model.PageRequest;
import co.edu.corhuila.marketagri.catalog.domain.model.Product;
import java.util.UUID;

/** Anyone browses the catalog (HU-CAT-005): E-10 list and E-11 detail. */
public interface BrowseProducts {

    Page<Product> list(PageRequest request);

    /** The product, or {@code ProductNotFoundException}. */
    Product get(UUID id);
}
