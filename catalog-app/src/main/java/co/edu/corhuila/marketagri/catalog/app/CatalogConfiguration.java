package co.edu.corhuila.marketagri.catalog.app;

import org.springframework.context.annotation.Configuration;

/**
 * The only place that knows the concrete types: use cases of the core are wired to the
 * outbound adapters here, with {@code @Bean} methods.
 */
@Configuration
public class CatalogConfiguration {
}
