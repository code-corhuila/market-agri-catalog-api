package co.edu.corhuila.marketagri.catalog.application.usecase;

import co.edu.corhuila.marketagri.catalog.application.port.in.PublishProduct;
import co.edu.corhuila.marketagri.catalog.application.port.out.ProductPublications;
import co.edu.corhuila.marketagri.catalog.application.port.out.ProductRepository;
import co.edu.corhuila.marketagri.catalog.application.port.out.UnitOfWork;
import co.edu.corhuila.marketagri.catalog.domain.model.Money;
import co.edu.corhuila.marketagri.catalog.domain.model.Product;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductNotFoundException;
import co.edu.corhuila.marketagri.catalog.domain.model.Quantity;
import java.time.Clock;
import java.util.UUID;

/**
 * E-13 as D-C35 describes it: the product and its key are inserted in one transaction; if the key
 * was already used, the transaction rolls back (the new product disappears) and the original
 * product is answered.
 */
public final class PublishProductService implements PublishProduct {

    private final ProductPublications publications;
    private final ProductRepository products;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public PublishProductService(ProductPublications publications, ProductRepository products,
                                 UnitOfWork unitOfWork, Clock clock) {
        this.publications = publications;
        this.products = products;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    @Override
    public Publication publish(Command command) {
        Money price = Money.ofCents(command.priceCents());
        Quantity quantity = new Quantity(command.quantity());
        Product product = Product.create(UUID.randomUUID(), command.producerId(), command.producerName(),
                command.name(), command.category(), command.unit(), quantity, price, command.municipality(),
                clock.instant());
        try {
            return unitOfWork.inTransaction(() -> {
                publications.add(product);
                if (!publications.claimKey(command.producerId(), command.idempotencyKey(), product.id())) {
                    throw new KeyAlreadyUsed();
                }
                return new Publication(product, true);
            });
        } catch (KeyAlreadyUsed replay) {
            UUID original = publications.productForKey(command.producerId(), command.idempotencyKey())
                    .orElseThrow();
            return new Publication(products.findById(original)
                    .orElseThrow(() -> new ProductNotFoundException(original)), false);
        }
    }

    /** Only rolls the unit of work back; never leaves this class. */
    private static final class KeyAlreadyUsed extends RuntimeException {
        KeyAlreadyUsed() {
            super(null, null, false, false);
        }
    }
}
