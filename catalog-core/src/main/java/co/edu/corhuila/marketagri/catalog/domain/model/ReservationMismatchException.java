package co.edu.corhuila.marketagri.catalog.domain.model;

import java.util.UUID;

/** The same {@code transactionId} with another product or quantity (I-03, {@code 409 RESERVATION_MISMATCH}). */
public final class ReservationMismatchException extends DomainException {

    public ReservationMismatchException(UUID transactionId) {
        super("RESERVATION_MISMATCH", "reservation exists with other data: " + transactionId);
    }
}
