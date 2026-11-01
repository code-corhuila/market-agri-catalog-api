package co.edu.corhuila.marketagri.catalog.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** INV-005 and ADR-029: a price is an integer number of centavos, greater than zero. */
class MoneyTest {

    @Test
    void keepsTheCentavos() {
        assertThat(Money.ofCents(8_500_000).cents()).isEqualTo(8_500_000L);
    }

    @Test
    void zeroIsPriceNotPositive() {
        assertThatThrownBy(() -> Money.ofCents(0))
                .isInstanceOfSatisfying(DomainException.class,
                        refused -> assertThat(refused.code()).isEqualTo("PRICE_NOT_POSITIVE"));
    }

    @Test
    void negativeIsPriceNotPositive() {
        assertThatThrownBy(() -> Money.ofCents(-100)).isInstanceOf(DomainException.class);
    }
}
