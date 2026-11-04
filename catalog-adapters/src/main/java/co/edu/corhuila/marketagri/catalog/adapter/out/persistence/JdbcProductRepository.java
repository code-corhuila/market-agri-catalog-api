package co.edu.corhuila.marketagri.catalog.adapter.out.persistence;

import co.edu.corhuila.marketagri.catalog.application.port.out.ProductEdits;
import co.edu.corhuila.marketagri.catalog.application.port.out.ProductPhotos;
import co.edu.corhuila.marketagri.catalog.application.port.out.ProductRemovals;
import co.edu.corhuila.marketagri.catalog.application.port.out.ProductRepository;
import co.edu.corhuila.marketagri.catalog.application.port.out.ProductStock;
import co.edu.corhuila.marketagri.catalog.domain.model.Money;
import co.edu.corhuila.marketagri.catalog.domain.model.Page;
import co.edu.corhuila.marketagri.catalog.domain.model.PageRequest;
import co.edu.corhuila.marketagri.catalog.domain.model.Product;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductCategory;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductFilter;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductUnit;
import co.edu.corhuila.marketagri.catalog.domain.model.Quantity;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * {@link ProductRepository}, {@link ProductStock}, {@link ProductPhotos}, {@link ProductEdits} and
 * {@link ProductRemovals} on the
 * {@code catalog.product} table of market-agri-catalog-db, as
 * catalog_app. Every query filters {@code deleted_at IS NULL} (INV-007); the list uses the indexes
 * {@code idx_product_*_created_at_id} of catalog-db. The status column is not read: {@link Product#restore}
 * derives it again from the quantity (INV-009); {@link #updateStock} writes the derived one.
 */
public final class JdbcProductRepository implements ProductRepository, ProductStock, ProductPhotos,
        ProductEdits, ProductRemovals {

    private static final String COLUMNS = """
            id, producer_id, producer_name, name, category, unit, quantity, price_cents,
            municipality, photo_url, created_at, updated_at""";

    private final JdbcClient jdbc;

    public JdbcProductRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Each filter adds one fixed fragment to the {@code WHERE}; its value always travels as a
     * parameter, never inside the SQL. The status is compared through the quantity (INV-009), as
     * {@link ProductFilter#matches} does. The page and the total use the same conditions.
     */
    @Override
    public Page<Product> findPage(ProductFilter filter, PageRequest request) {
        StringBuilder where = new StringBuilder("deleted_at IS NULL");
        Map<String, Object> params = new HashMap<>();
        if (filter.category() != null) {
            where.append(" AND category = :category");
            params.put("category", filter.category().name());
        }
        if (filter.municipality() != null) {
            where.append(" AND municipality_key = :municipalityKey");
            params.put("municipalityKey", filter.municipality().value());
        }
        if (filter.status() != null) {
            where.append(switch (filter.status()) {
                case ACTIVE -> " AND quantity > 0";
                case OUT_OF_STOCK -> " AND quantity = 0";
            });
        }
        if (filter.producer() != null) {
            where.append(" AND producer_id = :producerId");
            params.put("producerId", filter.producer());
        }
        List<Product> items = jdbc.sql("SELECT " + COLUMNS + " FROM catalog.product WHERE " + where
                        + " ORDER BY created_at DESC, id ASC LIMIT :limit OFFSET :offset")
                .params(params)
                .param("limit", request.limit())
                .param("offset", request.offset())
                .query(JdbcProductRepository::toProduct)
                .list();
        long total = jdbc.sql("SELECT count(*) FROM catalog.product WHERE " + where)
                .params(params)
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

    /** The municipality key is written again with the municipality, as on creation (D-16). */
    @Override
    public void updateDetails(Product product) {
        jdbc.sql("""
                UPDATE catalog.product SET name = :name, category = :category, unit = :unit, quantity = :quantity,
                    status = :status, price_cents = :priceCents, municipality = :municipality,
                    municipality_key = :municipalityKey, updated_at = :updatedAt
                WHERE id = :id""")
                .param("name", product.name())
                .param("category", product.category().name())
                .param("unit", product.unit().name())
                .param("quantity", product.quantity().value())
                .param("status", product.status().name())
                .param("priceCents", product.price().cents())
                .param("municipality", product.municipality())
                .param("municipalityKey", product.municipalityKey().value())
                .param("updatedAt", product.updatedAt().atOffset(ZoneOffset.UTC))
                .param("id", product.id())
                .update();
    }

    /** E-18: logical deletion (catalog_app has no DELETE); {@code deleted_at IS NULL} makes it once. */
    @Override
    public void markDeleted(UUID id, Instant now) {
        jdbc.sql("""
                UPDATE catalog.product SET deleted_at = :now, updated_at = :now
                WHERE id = :id AND deleted_at IS NULL""")
                .param("now", now.atOffset(ZoneOffset.UTC))
                .param("id", id)
                .update();
    }

    @Override
    public void updatePhoto(Product product) {
        jdbc.sql("UPDATE catalog.product SET photo_url = :photoUrl, updated_at = :updatedAt WHERE id = :id")
                .param("photoUrl", product.photoUrl())
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
