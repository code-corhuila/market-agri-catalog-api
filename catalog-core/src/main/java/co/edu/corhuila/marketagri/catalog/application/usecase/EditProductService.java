package co.edu.corhuila.marketagri.catalog.application.usecase;

import co.edu.corhuila.marketagri.catalog.application.port.in.EditProduct;
import co.edu.corhuila.marketagri.catalog.application.port.out.ProductEdits;
import co.edu.corhuila.marketagri.catalog.application.port.out.UnitOfWork;
import co.edu.corhuila.marketagri.catalog.domain.model.Money;
import co.edu.corhuila.marketagri.catalog.domain.model.Product;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductNotFoundException;
import co.edu.corhuila.marketagri.catalog.domain.model.Quantity;
import java.time.Clock;

/**
 * E-14. The product is found and its owner checked before the new price and quantity are built:
 * that is the order of the contract (404, 403, then the 422 of the price and of the quantity), so a
 * producer learns nothing about someone else's product from a wrong value. All under the lock of
 * {@link ProductEdits#findByIdForUpdate}, in one {@link UnitOfWork}.
 */
public final class EditProductService implements EditProduct {

    private final ProductEdits products;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public EditProductService(ProductEdits products, UnitOfWork unitOfWork, Clock clock) {
        this.products = products;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    @Override
    public Product edit(Command command) {
        return unitOfWork.inTransaction(() -> {
            Product product = products.findByIdForUpdate(command.productId())
                    .orElseThrow(() -> new ProductNotFoundException(command.productId()));
            product.ensureOwnedBy(command.producerId());
            Money price = Money.ofCents(command.priceCents());
            Quantity quantity = new Quantity(command.quantity());
            product.edit(command.name(), command.category(), command.unit(), quantity, price,
                    command.municipality(), clock.instant());
            products.updateDetails(product);
            return product;
        });
    }
}
