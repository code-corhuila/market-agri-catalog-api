package co.edu.corhuila.marketagri.catalog.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** D-16: the key of the filter of E-10, and the same shape {@code chk_product_municipality_key} accepts. */
class MunicipalityKeyTest {

    private static String key(String municipality) {
        return MunicipalityKey.of(municipality).value();
    }

    @Test
    void removesAccentsAndLowercases() {
        assertThat(key("Garzón")).isEqualTo("garzon");
        assertThat(key("TIMANÁ")).isEqualTo("timana");
    }

    @Test
    void eneBecomesN() {
        assertThat(key("La Peña")).isEqualTo("la pena");
    }

    @Test
    void trimsAndCollapsesSpaces() {
        assertThat(key("  San   Agustín  ")).isEqualTo("san agustin");
    }

    @Test
    void keepsOneDotApostropheOrHyphenBetweenWords() {
        assertThat(key("Villa-Vieja")).isEqualTo("villa-vieja");
        assertThat(key("D'Ávila")).isEqualTo("d'avila");
        assertThat(key("D’Ávila")).isEqualTo("d'avila");
        assertThat(key("Sta.Rosa")).isEqualTo("sta.rosa");
    }

    @Test
    void anyOtherGapBecomesOneSpace() {
        assertThat(key("Sta. Rosa")).isEqualTo("sta rosa");
        assertThat(key("Garzón (Huila)")).isEqualTo("garzon huila");
        assertThat(key("Neiva, Huila")).isEqualTo("neiva huila");
        assertThat(key("Pitalito/Huila")).isEqualTo("pitalito huila");
        assertThat(key("Villa--Vieja")).isEqualTo("villa vieja");
    }

    @Test
    void keepsDigits() {
        assertThat(key("Vereda 3 de Mayo")).isEqualTo("vereda 3 de mayo");
    }

    @Test
    void withoutLettersOrDigitsIsRefused() {
        assertThatThrownBy(() -> MunicipalityKey.of(" (?) ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aKeyThatIsNotNormalizedIsRefused() {
        assertThatThrownBy(() -> new MunicipalityKey("Garzón")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MunicipalityKey("san  agustin")).isInstanceOf(IllegalArgumentException.class);
    }
}
