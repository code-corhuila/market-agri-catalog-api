package co.edu.corhuila.marketagri.catalog.app;

import co.edu.corhuila.marketagri.catalog.adapter.out.persistence.JdbcDatabaseCheck;
import co.edu.corhuila.marketagri.catalog.application.port.out.ReadinessCheck;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The only place that knows the concrete types: use cases of the core are wired to the
 * outbound adapters here, with {@code @Bean} methods.
 */
@Configuration
public class CatalogConfiguration {

    /** The pool is Spring Boot's Hikari, with the limits of application.yml. */
    @Bean
    ReadinessCheck databaseCheck(DataSource dataSource) {
        return new JdbcDatabaseCheck(dataSource);
    }
}
