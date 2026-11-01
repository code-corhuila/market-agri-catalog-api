package co.edu.corhuila.marketagri.catalog.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * An amount of a product in its {@link ProductUnit}: a decimal with at most two places (D-C7:
 * 12.5 kg), never negative, at most 9 999 999 999.99 (the column is {@code numeric(12,2)}).
 * Always kept with scale 2, so {@code 5} and {@code 5.00} are equal. Exact arithmetic only.
 */
public record Quantity(BigDecimal value) {

    private static final int SCALE = 2;
    private static final BigDecimal MAX = new BigDecimal("9999999999.99");

    public Quantity {
        if (value.signum() < 0) {
            throw new DomainException("QUANTITY_NEGATIVE", "quantity must not be negative: " + value);
        }
        if (value.stripTrailingZeros().scale() > SCALE) {
            throw new IllegalArgumentException("quantity has more than two decimals: " + value);
        }
        if (value.compareTo(MAX) > 0) {
            throw new IllegalArgumentException("quantity above " + MAX + ": " + value);
        }
        value = value.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    public static Quantity of(String value) {
        return new Quantity(new BigDecimal(value));
    }

    public boolean isZero() {
        return value.signum() == 0;
    }

    public boolean isGreaterThan(Quantity other) {
        return value.compareTo(other.value) > 0;
    }

    public Quantity plus(Quantity other) {
        return new Quantity(value.add(other.value));
    }

    public Quantity minus(Quantity other) {
        return new Quantity(value.subtract(other.value));
    }

    @Override
    public String toString() {
        return value.toPlainString();
    }
}
