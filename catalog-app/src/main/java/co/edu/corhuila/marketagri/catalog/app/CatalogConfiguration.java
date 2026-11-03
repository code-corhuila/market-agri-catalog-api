package co.edu.corhuila.marketagri.catalog.app;

import co.edu.corhuila.marketagri.catalog.adapter.in.http.PublicKeys;
import co.edu.corhuila.marketagri.catalog.adapter.in.http.Rs256Verifier;
import co.edu.corhuila.marketagri.catalog.adapter.out.persistence.JdbcDatabaseCheck;
import co.edu.corhuila.marketagri.catalog.adapter.out.persistence.JdbcProductRepository;
import co.edu.corhuila.marketagri.catalog.adapter.out.persistence.JdbcReservationRepository;
import co.edu.corhuila.marketagri.catalog.adapter.out.persistence.SpringUnitOfWork;
import co.edu.corhuila.marketagri.catalog.application.port.in.BrowseProducts;
import co.edu.corhuila.marketagri.catalog.application.port.in.ReleaseStock;
import co.edu.corhuila.marketagri.catalog.application.port.in.ReserveStock;
import co.edu.corhuila.marketagri.catalog.application.port.out.ProductRepository;
import co.edu.corhuila.marketagri.catalog.application.port.out.ProductStock;
import co.edu.corhuila.marketagri.catalog.application.port.out.ReadinessCheck;
import co.edu.corhuila.marketagri.catalog.application.port.out.ReservationRepository;
import co.edu.corhuila.marketagri.catalog.application.port.out.UnitOfWork;
import co.edu.corhuila.marketagri.catalog.application.usecase.BrowseProductsService;
import co.edu.corhuila.marketagri.catalog.application.usecase.StockReservationService;
import java.nio.file.Path;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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

    /** Declared with its class: it is both {@link ProductRepository} and {@link ProductStock}. */
    @Bean
    JdbcProductRepository productRepository(JdbcClient jdbc) {
        return new JdbcProductRepository(jdbc);
    }

    @Bean
    BrowseProducts browseProducts(ProductRepository products) {
        return new BrowseProductsService(products);
    }

    @Bean
    ReservationRepository reservationRepository(JdbcClient jdbc) {
        return new JdbcReservationRepository(jdbc);
    }

    /** Spring Boot's transaction manager of the DataSource: the same connection JdbcClient uses. */
    @Bean
    UnitOfWork unitOfWork(PlatformTransactionManager transactionManager) {
        return new SpringUnitOfWork(new TransactionTemplate(transactionManager));
    }

    /** One bean for I-03 and I-04: it is both {@link ReserveStock} and {@link ReleaseStock}. */
    @Bean
    StockReservationService stockReservations(ProductStock stock, ReservationRepository reservations,
                                              UnitOfWork unitOfWork) {
        return new StockReservationService(stock, reservations, unitOfWork, Clock.systemUTC());
    }

    /**
     * Validator of the tokens of auth (ADR-020): the issuer and the public key by configuration,
     * JWT_PUBLIC_KEY first, JWT_PUBLIC_KEY_FILE otherwise. Without a key the service does not start.
     */
    @Bean
    Rs256Verifier tokenVerifier(@Value("${jwt.issuer}") String issuer,
                                @Value("${jwt.public-key:}") String publicKey,
                                @Value("${jwt.public-key-file:}") String publicKeyFile) {
        RSAPublicKey key;
        if (!publicKey.isBlank()) {
            key = PublicKeys.fromPem(publicKey);
        } else if (!publicKeyFile.isBlank()) {
            key = PublicKeys.fromFile(Path.of(publicKeyFile));
        } else {
            throw new IllegalStateException("Set JWT_PUBLIC_KEY or JWT_PUBLIC_KEY_FILE");
        }
        return new Rs256Verifier(key, issuer, Clock.systemUTC());
    }
}
