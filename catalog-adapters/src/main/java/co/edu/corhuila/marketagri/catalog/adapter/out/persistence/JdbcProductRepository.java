package co.edu.corhuila.marketagri.catalog.adapter.out.persistence;

import co.edu.corhuila.marketagri.catalog.application.port.out.ProductRepository;
import co.edu.corhuila.marketagri.catalog.application.port.out.ProductStock;
import co.edu.corhuila.marketagri.catalog.domain.model.Money;
import co.edu.corhuila.marketagri.catalog.domain.model.Page;
import co.edu.corhuila.marketagri.catalog.domain.model.PageRequest;
import co.edu.corhuila.marketagri.catalog.domain.model.Product;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductCategory;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductUnit;
import co.edu.corhuila.marketagri.catalog.domain.model.Quantity;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * {@link ProductRepository} and {@link ProductStock} on the {@code catalog.product} table of market-agri-catalog-db, as
 * catalog_app. Every query filters {@code deleted_at IS NULL} (INV-007); the list uses the index
 * {@code idx_product_created_at_id}. The status column is not read: {@link Product#restore}
 * derives it again from the quantity (INV-009); {@link #updateStock} writes the derived one.
 */
public final class JdbcProductRepository implements ProductRepository, ProductStock {

    private static final String COLUMNS = """
            id, producer_id, producer_name, name, category, unit, quantity, price_cents,
            municipality, photo_url, created_at, updated_at""";

    private final JdbcClient jdbc;

    public JdbcProductRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Page<Product> findPage(PageRequest request) {
        List<Product> items = jdbc.sql("SELECT " + COLUMNS + """

                FROM catalog.product
                WHERE deleted_at IS NULL
                ORDER BY created_at DESC, id ASC
                LIMIT :limit OFFSET :offset""")
                .param("limit", request.limit())
                .param("offset", request.offset())
                .query(JdbcProductRepository::toProduct)
                .list();
        long total = jdbc.sql("SELECT count(*) FROM catalog.product WHERE deleted_at IS NULL")
                .query(Long.class)
                .single();
        return new Page<>(items, request, total);
    }

    @Override
    public Optional<Product> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM catalog.product WHERE id = :id AND deleted_at IS NULL")
                .param("id", id)
                .query(JdbcProductRepository::toProduct)
                .optional();
    }

    /** {@code FOR UPDATE}: the row stays locked until the transaction of the caller ends. */
    @Override
    public Optional<Product> findByIdForUpdate(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS
                        + " FROM catalog.product WHERE id = :id AND deleted_at IS NULL FOR UPDATE")
                .param("id", id)
                .query(JdbcProductRepository::toProduct)
                .optional();
    }

    @Override
    public void updateStock(Product product) {
        jdbc.sql("""
                UPDATE catalog.product SET quantity = :quantity, status = :status, updated_at = :updatedAt
                WHERE id = :id""")
                .param("quantity", product.quantity().value())
                .param("status", product.status().name())
                .param("updatedAt", product.updatedAt().atOffset(ZoneOffset.UTC))
                .param("id", product.id())
                .update();
    }

    private static Product toProduct(ResultSet row, int rowNumber) throws SQLException {
        return Product.restore(
                row.getObject("id", UUID.class),
                row.getObject("producer_id", UUID.class),
                row.getString("producer_name"),
                row.getString("name"),
                ProductCategory.valueOf(row.getString("category")),
                ProductUnit.valueOf(row.getString("unit")),
                new Quantity(row.getBigDecimal("quantity")),
                Money.ofCents(row.getLong("price_cents")),
                row.getString("municipality"),
                row.getString("photo_url"),
                row.getObject("created_at", OffsetDateTime.class).toInstant(),
                row.getObject("updated_at", OffsetDateTime.class).toInstant());
    }
}
