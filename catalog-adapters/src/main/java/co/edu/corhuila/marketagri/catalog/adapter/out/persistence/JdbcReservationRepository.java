package co.edu.corhuila.marketagri.catalog.adapter.out.persistence;

import co.edu.corhuila.marketagri.catalog.application.port.out.ReservationRepository;
import co.edu.corhuila.marketagri.catalog.domain.model.Money;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductUnit;
import co.edu.corhuila.marketagri.catalog.domain.model.Quantity;
import co.edu.corhuila.marketagri.catalog.domain.model.StockReservation;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * {@link ReservationRepository} on {@code catalog.stock_reservation}, as catalog_app (no DELETE:
 * a release only sets {@code released_at}). The primary key {@code transaction_id} is the
 * idempotency key of I-03.
 */
public final class JdbcReservationRepository implements ReservationRepository {

    private static final String COLUMNS = """
            transaction_id, product_id, producer_id, product_name, unit, quantity, unit_price_cents,
            remaining_quantity, created_at""";

    private final JdbcClient jdbc;

    public JdbcReservationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<StockReservation> findByTransactionId(UUID transactionId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM catalog.stock_reservation WHERE transaction_id = :id")
                .param("id", transactionId)
                .query(JdbcReservationRepository::toReservation)
                .optional();
    }

    @Override
    public boolean add(StockReservation reservation) {
        // ON CONFLICT: a concurrent insert of the same key answers false instead of an SQL error.
        int inserted = jdbc.sql("INSERT INTO catalog.stock_reservation (" + COLUMNS + """
                )
                VALUES (:transactionId, :productId, :producerId, :productName, :unit, :quantity,
                        :unitPriceCents, :remainingQuantity, :createdAt)
                ON CONFLICT (transaction_id) DO NOTHING""")
                .param("transactionId", reservation.transactionId())
                .param("productId", reservation.productId())
                .param("producerId", reservation.producerId())
                .param("productName", reservation.productName())
                .param("unit", reservation.unit().name())
                .param("quantity", reservation.quantity().value())
                .param("unitPriceCents", reservation.unitPrice().cents())
                .param("remainingQuantity", reservation.remainingQuantity().value())
                .param("createdAt", utc(reservation.createdAt()))
                .update();
        return inserted == 1;
    }

    @Override
    public Optional<StockReservation> release(UUID transactionId, Instant now) {
        // The row lock of the UPDATE makes a second, concurrent release wait and then match no row.
        return jdbc.sql("""
                UPDATE catalog.stock_reservation SET released_at = :now
                WHERE transaction_id = :id AND released_at IS NULL
                RETURNING\s""" + COLUMNS)
                .param("now", utc(now))
                .param("id", transactionId)
                .query(JdbcReservationRepository::toReservation)
                .optional();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static StockReservation toReservation(ResultSet row, int rowNumber) throws SQLException {
        return new StockReservation(
                row.getObject("transaction_id", UUID.class),
                row.getObject("product_id", UUID.class),
                row.getObject("producer_id", UUID.class),
                row.getString("product_name"),
                ProductUnit.valueOf(row.getString("unit")),
                new Quantity(row.getBigDecimal("quantity")),
                Money.ofCents(row.getLong("unit_price_cents")),
                new Quantity(row.getBigDecimal("remaining_quantity")),
                row.getObject("created_at", OffsetDateTime.class).toInstant());
    }
}
