package co.edu.corhuila.marketagri.catalog.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.marketagri.catalog.application.port.in.EditProduct.Command;
import co.edu.corhuila.marketagri.catalog.application.port.out.ProductEdits;
import co.edu.corhuila.marketagri.catalog.application.port.out.UnitOfWork;
import co.edu.corhuila.marketagri.catalog.domain.model.DomainException;
import co.edu.corhuila.marketagri.catalog.domain.model.Money;
import co.edu.corhuila.marketagri.catalog.domain.model.Product;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductCategory;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductStatus;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductUnit;
import co.edu.corhuila.marketagri.catalog.domain.model.Quantity;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** E-14 in the core: the owner's edit, under the lock, with the errors in the order of the contract. */
class EditProductServiceTest {

    private static final Instant CREATED = Instant.parse("2026-11-01T15:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-04T15:00:00Z");
    private static final UUID ANA = UUID.randomUUID();
    private static final UUID LUIS = UUID.randomUUID();

    /** One product; remembers whether it was read under the lock and written inside the unit of work. */
    private static final class FakeStore implements ProductEdits, UnitOfWork {
        final Product product = Product.create(UUID.randomUUID(), ANA, "Ana Ruiz", "Café pergamino seco",
                ProductCategory.CAFE, ProductUnit.ARROBA, Quantity.of("40"), Money.ofCents(8_500_000), "Garzón",
                CREATED);
        boolean inTransaction;
        boolean lockedInTransaction;
        int updates;

        @Override
        public Optional<Product> findByIdForUpdate(UUID id) {
            lockedInTransaction = inTransaction;
            return product.id().equals(id) ? Optional.of(product) : Optional.empty();
        }

        @Override
        public void updateDetails(Product edited) {
            assertThat(inTransaction).as("written inside the unit of work").isTrue();
            updates++;
        }

        @Override
        public <T> T inTransaction(Supplier<T> work) {
            inTransaction = true;
            try {
                return work.get();
            } finally {
                inTransaction = false;
            }
        }
    }

    private final FakeStore store = new FakeStore();
    private final EditProductService service = new EditProductService(store, store, Clock.fixed(NOW, ZoneOffset.UTC));

    private Command command(UUID productId, UUID producer, String quantity, long priceCents) {
        return new Command(productId, producer, "Café lavado", ProductCategory.CAFE, ProductUnit.KILOGRAMO,
                new BigDecimal(quantity), priceCents, "Pitalito");
    }

    private static String code(Throwable refused) {
        return ((DomainException) refused).code();
    }

    @Test
    void ownerEditToZeroIsOutOfStockAndIsStored() {
        Product edited = service.edit(command(store.product.id(), ANA, "0", 9_000_000));

        assertThat(edited.status()).isEqualTo(ProductStatus.OUT_OF_STOCK);
        assertThat(edited.name()).isEqualTo("Café lavado");
        assertThat(edited.price()).isEqualTo(Money.ofCents(9_000_000));
        assertThat(edited.updatedAt()).isEqualTo(NOW);
        assertThat(store.lockedInTransaction).isTrue();
        assertThat(store.updates).isEqualTo(1);
    }

    @Test
    void unknownProductIsNotFound() {
        assertThatThrownBy(() -> service.edit(command(UUID.randomUUID(), ANA, "1", 1)))
                .satisfies(refused -> assertThat(code(refused)).isEqualTo("PRODUCT_NOT_FOUND"));
    }

    @Test
    void someoneElsesProductIsNotProductOwnerEvenWithAWrongPriceAndChangesNothing() {
        assertThatThrownBy(() -> service.edit(command(store.product.id(), LUIS, "-1", 0)))
                .satisfies(refused -> assertThat(code(refused)).isEqualTo("NOT_PRODUCT_OWNER"));

        assertThat(store.product.name()).isEqualTo("Café pergamino seco");
        assertThat(store.updates).isZero();
    }

    @Test
    void priceIsCheckedBeforeQuantity() {
        assertThatThrownBy(() -> service.edit(command(store.product.id(), ANA, "-1", 0)))
                .satisfies(refused -> assertThat(code(refused)).isEqualTo("PRICE_NOT_POSITIVE"));
        assertThatThrownBy(() -> service.edit(command(store.product.id(), ANA, "-1", 1)))
                .satisfies(refused -> assertThat(code(refused)).isEqualTo("QUANTITY_NEGATIVE"));

        assertThat(store.product.quantity()).isEqualTo(Quantity.of("40"));
        assertThat(store.updates).isZero();
    }
}
