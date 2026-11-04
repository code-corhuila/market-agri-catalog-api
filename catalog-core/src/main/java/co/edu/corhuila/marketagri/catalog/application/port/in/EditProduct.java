package co.edu.corhuila.marketagri.catalog.application.port.in;

import co.edu.corhuila.marketagri.catalog.domain.model.Product;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductCategory;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductUnit;
import java.math.BigDecimal;
import java.util.UUID;

/** The owner replaces the details of a product (E-14, HU-CAT-002). */
public interface EditProduct {

    /**
     * The edited product. In the order of the contract: {@code PRODUCT_NOT_FOUND},
     * {@code NOT_PRODUCT_OWNER}, {@code PRICE_NOT_POSITIVE}, {@code QUANTITY_NEGATIVE}.
     */
    Product edit(Command command);

    record Command(UUID productId, UUID producerId, String name, ProductCategory category, ProductUnit unit,
                   BigDecimal quantity, long priceCents, String municipality) {
    }
}
