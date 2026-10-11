package co.edu.corhuila.marketagri.catalog.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Composition root. Scans the app and the adapters; the core carries no Spring annotation,
 * so its types are instantiated explicitly in {@link CatalogConfiguration}.
 */
@SpringBootApplication(scanBasePackages = {
        "co.edu.corhuila.marketagri.catalog.app",
        "co.edu.corhuila.marketagri.catalog.adapter"})
public class CatalogApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogApplication.class, args);
    }
}
