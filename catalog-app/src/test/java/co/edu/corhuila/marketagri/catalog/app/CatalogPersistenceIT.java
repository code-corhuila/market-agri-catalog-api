package co.edu.corhuila.marketagri.catalog.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Against a real PostgreSQL with the schema of market-agri-catalog-db, reached through
 * TEST_DATABASE_URL (Anexo C, "Pruebas"); skipped when the variable is not set. The URL carries
 * the catalog_app user and password: `bash scripts/test-database.sh` prints one.
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class CatalogPersistenceIT extends CatalogSpringTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TEST_DATABASE_URL"));
        // Empty: the user and password come from the URL itself.
        registry.add("spring.datasource.username", () -> "");
        registry.add("spring.datasource.password", () -> "");
    }

    @Autowired
    private MockMvc http;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void readyIs200WithTheDatabaseUp() throws Exception {
        http.perform(get("/health/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.checks.database").value("UP"));
    }

    @Test
    void connectsAsCatalogAppInsideTheCatalogSchema() {
        Map<String, Object> session = jdbc.queryForMap("SELECT current_user AS who, current_schema() AS schema");

        assertThat(session).containsEntry("who", "catalog_app").containsEntry("schema", "catalog");
    }

    @Test
    void seesTheTablesOfCatalogDb() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product", Long.class)).isNotNull();
    }

    @Test
    void everyConnectionCarriesTheStatementTimeout() {
        assertThat(jdbc.queryForObject("SHOW statement_timeout", String.class)).isEqualTo("5s");
    }

    @Test
    void aStatementLongerThanTheLimitIsCancelled() {
        assertThatThrownBy(() -> jdbc.execute("SELECT pg_sleep(6)"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("statement timeout");
    }
}
