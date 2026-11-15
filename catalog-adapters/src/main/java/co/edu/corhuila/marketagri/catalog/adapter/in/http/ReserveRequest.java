package co.edu.corhuila.marketagri.catalog.adapter.in.http;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Body of I-03. Only the shape is checked here ({@code 400}); {@code quantity > 0} is a rule of the
 * core ({@code 422 QUANTITY_NOT_POSITIVE}), so it is not a constraint of this record.
 */
public record ReserveRequest(
        @NotNull(message = "Campo obligatorio.")
        UUID transactionId,
        @NotNull(message = "Campo obligatorio.")
        @Digits(integer = 10, fraction = 2, message = "Debe ser un número con máximo dos decimales.")
        BigDecimal quantity) {
}
