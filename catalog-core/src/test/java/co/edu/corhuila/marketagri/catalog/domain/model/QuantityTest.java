package co.edu.corhuila.marketagri.catalog.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** D-C7: a quantity is a decimal with at most two places (12.5 kg), never negative. */
class QuantityTest {

    @Test
    void sameValueWithDifferentScaleIsEqual() {
        assertThat(Quantity.of("5")).isEqualTo(Quantity.of("5.00"));
    }

    @Test
    void negativeIsQuantityNegative() {
        assertThatThrownBy(() -> Quantity.of("-1"))
                .isInstanceOfSatisfying(DomainException.class,
                        refused -> assertThat(refused.code()).isEqualTo("QUANTITY_NEGATIVE"));
    }

    @Test
    void moreThanTwoDecimalsIsRefused() {
        assertThatThrownBy(() -> Quantity.of("1.005")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aboveTheLimitIsRefused() {
        assertThatThrownBy(() -> Quantity.of("10000000000")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addsAndSubtractsExactly() {
        assertThat(Quantity.of("12.5").minus(Quantity.of("0.25"))).isEqualTo(Quantity.of("12.25"));
        assertThat(Quantity.of("0.1").plus(Quantity.of("0.2"))).isEqualTo(Quantity.of("0.3"));
    }
}
