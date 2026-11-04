package co.edu.corhuila.marketagri.catalog.app;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** E-19 and E-20 (D-C6): anonymous, a plain array, the ten codes and labels in the contract order. */
@SpringBootTest
@AutoConfigureMockMvc
class CatalogLookupsTest extends CatalogSpringTest {

    @Autowired
    private MockMvc http;

    @Test
    void categoriesInTheContractOrder() throws Exception {
        http.perform(get("/api/v1/catalog/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code").value(contains("FRUTAS", "VERDURAS", "HORTALIZAS",
                        "TUBERCULOS", "GRANOS_Y_CEREALES", "CAFE", "CACAO", "LACTEOS", "HIERBAS_AROMATICAS",
                        "OTROS")))
                .andExpect(jsonPath("$[*].label").value(contains("Frutas", "Verduras", "Hortalizas",
                        "Tubérculos", "Granos y cereales", "Café", "Cacao", "Lácteos", "Hierbas aromáticas",
                        "Otros")));
    }

    @Test
    void unitsInTheContractOrder() throws Exception {
        http.perform(get("/api/v1/catalog/units"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code").value(contains("KILOGRAMO", "LIBRA", "ARROBA", "BULTO",
                        "CANASTA", "CAJA", "DOCENA", "MANOJO", "LITRO", "UNIDAD")))
                .andExpect(jsonPath("$[*].label").value(contains("Kilogramo", "Libra", "Arroba", "Bulto",
                        "Canasta", "Caja", "Docena", "Manojo", "Litro", "Unidad")));
    }

    @Test
    void anonymousEvenWithAnExpiredToken() throws Exception {
        http.perform(get("/api/v1/catalog/units").header("Authorization",
                        "Bearer " + TestTokens.user("BUYER").expiredSecondsAgo(3600).signed()))
                .andExpect(status().isOk());
    }
}
