package co.edu.corhuila.marketagri.catalog.app;

import co.edu.corhuila.marketagri.catalog.application.port.out.ReservationRepository;
import co.edu.corhuila.marketagri.catalog.domain.model.StockReservation;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** The reservation port in memory, for the HTTP tests: the same "once" as the table. */
final class InMemoryReservationRepository implements ReservationRepository {

    private final Map<UUID, StockReservation> reservations = new HashMap<>();
    private final Set<UUID> released = new HashSet<>();

    void clear() {
        reservations.clear();
        released.clear();
    }

    @Override
    public Optional<StockReservation> findByTransactionId(UUID transactionId) {
        return Optional.ofNullable(reservations.get(transactionId));
    }

    @Override
    public boolean add(StockReservation reservation) {
        return reservations.putIfAbsent(reservation.transactionId(), reservation) == null;
    }

    @Override
    public Optional<StockReservation> release(UUID transactionId, Instant now) {
        if (!reservations.containsKey(transactionId) || !released.add(transactionId)) {
            return Optional.empty();
        }
        return Optional.of(reservations.get(transactionId));
    }
}
