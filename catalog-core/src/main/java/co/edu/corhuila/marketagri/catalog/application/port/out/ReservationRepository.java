package co.edu.corhuila.marketagri.catalog.application.port.out;

import co.edu.corhuila.marketagri.catalog.domain.model.StockReservation;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Reservations of stock, by the {@code transactionId} of the saga (their idempotency key). */
public interface ReservationRepository {

    Optional<StockReservation> findByTransactionId(UUID transactionId);

    /** False when the {@code transactionId} is already stored: nothing is written. */
    boolean add(StockReservation reservation);

    /**
     * Marks it released if it still holds stock, at most once even when two calls race. Answers the
     * reservation only to the call that released it; empty when unknown or already released.
     */
    Optional<StockReservation> release(UUID transactionId, Instant now);
}
