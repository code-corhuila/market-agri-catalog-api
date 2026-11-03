package co.edu.corhuila.marketagri.catalog.adapter.in.http;

import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.HttpStatus;

/** Codes of the catalog rules (api-contract §4.2), with their status and Spanish message. */
enum DomainError {
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "El producto no existe."),
    PRICE_NOT_POSITIVE(HttpStatus.UNPROCESSABLE_ENTITY, "El precio debe ser mayor que cero."),
    QUANTITY_NEGATIVE(HttpStatus.UNPROCESSABLE_ENTITY, "La cantidad no puede ser negativa."),
    QUANTITY_NOT_POSITIVE(HttpStatus.UNPROCESSABLE_ENTITY, "La cantidad debe ser mayor que cero."),
    RESERVATION_MISMATCH(HttpStatus.CONFLICT, "La reserva ya existe con otros datos."),
    PRODUCT_NOT_AVAILABLE(HttpStatus.CONFLICT, "El producto no está disponible para la venta."),
    /** {@code %s}: available, then requested. */
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "Cantidad insuficiente: hay %s disponibles y se solicitaron %s.");

    private final HttpStatus status;
    private final String message;

    DomainError(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    HttpStatus status() {
        return status;
    }

    String message(Object... values) {
        return message.formatted(values);
    }

    static Optional<DomainError> of(String code) {
        return Arrays.stream(values()).filter(error -> error.name().equals(code)).findFirst();
    }
}
