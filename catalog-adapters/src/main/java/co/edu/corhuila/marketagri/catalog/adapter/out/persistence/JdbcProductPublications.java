package co.edu.corhuila.marketagri.catalog.adapter.out.persistence;

import co.edu.corhuila.marketagri.catalog.application.port.out.ProductPublications;
import co.edu.corhuila.marketagri.catalog.domain.model.Product;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * {@link ProductPublications} on {@code catalog.product} and {@code catalog.idempotency_key}
 * (Anexo A, D-C35), as catalog_app. The primary key {@code (owner_id, key_value)} makes a second
 * use of a key, even a concurrent one, insert nothing.
 */
public final class JdbcProductPublications implements ProductPublications {

    private final JdbcClient jdbc;

    public JdbcProductPublications(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** {@code municipality_key} is written here, from the aggregate (D-16); the status is the derived one. */
    @Override
    public void add(Product product) {
        jdbc.sql("""
                INSERT INTO catalog.product (id, producer_id, producer_name, name, category, unit, quantity,
                    price_cents, municipality, municipality_key, photo_url, status, created_at, updated_at)
                VALUES (:id, :producerId, :producerName, :name, :category, :unit, :quantity,
                    :priceCents, :municipality, :municipalityKey, :photoUrl, :status, :createdAt, :updatedAt)""")
                .param("id", product.id())
                .param("producerId", product.producerId())
                .param("producerName", product.producerName())
                .param("name", product.name())
                .param("category", product.category().name())
                .param("unit", product.unit().name())
                .param("quantity", product.quantity().value())
                .param("priceCents", product.price().cents())
                .param("municipality", product.municipality())
                .param("municipalityKey", product.municipalityKey().value())
                .param("photoUrl", product.photoUrl())
                .param("status", product.status().name())
                .param("createdAt", product.createdAt().atOffset(ZoneOffset.UTC))
                .param("updatedAt", product.updatedAt().atOffset(ZoneOffset.UTC))
                .update();
    }

    @Override
    public boolean claimKey(UUID producerId, String idempotencyKey, UUID productId) {
        // A concurrent claim of the same key waits for the first transaction, then inserts nothing.
        return jdbc.sql("""
                INSERT INTO catalog.idempotency_key (owner_id, key_value, product_id)
                VALUES (:ownerId, :keyValue, :productId)
                ON CONFLICT (owner_id, key_value) DO NOTHING""")
                .param("ownerId", producerId)
                .param("keyValue", idempotencyKey)
                .param("productId", productId)
                .update() == 1;
    }

    @Override
    public Optional<UUID> productForKey(UUID producerId, String idempotencyKey) {
        return jdbc.sql("""
                SELECT product_id FROM catalog.idempotency_key
                WHERE owner_id = :ownerId AND key_value = :keyValue""")
                .param("ownerId", producerId)
                .param("keyValue", idempotencyKey)
                .query(UUID.class)
                .optional();
    }
}
