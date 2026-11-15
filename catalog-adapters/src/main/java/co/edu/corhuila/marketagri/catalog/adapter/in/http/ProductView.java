package co.edu.corhuila.marketagri.catalog.adapter.in.http;

import co.edu.corhuila.marketagri.catalog.domain.model.Product;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code ProductView} of api-contract §4.2: what the client sees of a product. The domain entity is
 * never serialized (Anexo C). Money in centavos with {@code currency: "COP"} (D-C25, ADR-029).
 */
public record ProductView(UUID id, UUID producerId, String producerName, String name, String category,
                          String unit, BigDecimal quantity, long priceCents, String currency,
                          String municipality, String status, String photoUrl, Instant createdAt,
                          Instant updatedAt) {

    private static final String CURRENCY = "COP";

    static ProductView of(Product product) {
        return new ProductView(product.id(), product.producerId(), product.producerName(), product.name(),
                product.category().name(), product.unit().name(), product.quantity().value(),
                product.price().cents(), CURRENCY, product.municipality(), product.status().name(),
                product.photoUrl(), product.createdAt(), product.updatedAt());
    }
}
