package co.edu.corhuila.marketagri.catalog.adapter.in.http;

import co.edu.corhuila.marketagri.catalog.domain.model.ProductCategory;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductUnit;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * {@code ProductWrite} of api-contract §4.2: only the shape ({@code 400}). {@code priceCents ≤ 0}
 * and {@code quantity < 0} are rules of the core ({@code 422}), so they are not constraints here.
 * An unknown enum value or a {@code priceCents} with decimals fails while reading the JSON
 * ({@code spring.jackson.deserialization.accept-float-as-int: false}). Unknown fields are ignored.
 */
public record ProductWrite(
        @NotBlank(message = "Campo obligatorio.")
        @Size(max = 150, message = "Máximo 150 caracteres.")
        String name,
        @NotNull(message = "Campo obligatorio.")
        ProductCategory category,
        @NotNull(message = "Campo obligatorio.")
        ProductUnit unit,
        @NotNull(message = "Campo obligatorio.")
        @Digits(integer = 10, fraction = 2, message = "Debe ser un número con máximo dos decimales.")
        BigDecimal quantity,
        @NotNull(message = "Campo obligatorio.")
        @Max(value = 999_999_999_999L, message = "Máximo 999999999999 centavos.")
        Long priceCents,
        @NotBlank(message = "Campo obligatorio.")
        @Size(max = 100, message = "Máximo 100 caracteres.")
        @Pattern(regexp = ".*[\\p{L}\\p{N}].*", message = "Debe tener al menos una letra o un número.")
        String municipality) {
}
