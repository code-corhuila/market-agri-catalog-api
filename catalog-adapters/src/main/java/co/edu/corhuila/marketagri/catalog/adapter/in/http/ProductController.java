package co.edu.corhuila.marketagri.catalog.adapter.in.http;

import co.edu.corhuila.marketagri.catalog.application.port.in.BrowseProducts;
import co.edu.corhuila.marketagri.catalog.application.port.in.PublishProduct;
import co.edu.corhuila.marketagri.catalog.domain.model.PageRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Products of the catalog: E-10 and E-11, anonymous (HU-CAT-005, D-C9), and E-13, a producer
 * publishing (HU-CAT-001).
 */
@RestController
@RequestMapping("/api/v1/catalog/products")
public class ProductController {

    private static final Set<String> LIST_PARAMETERS = Set.of("page", "limit");
    private static final String PRODUCTS = "/api/v1/catalog/products";
    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    private static final int KEY_MIN = 8;
    private static final int KEY_MAX = 128;

    private final BrowseProducts browse;
    private final PublishProduct publish;

    public ProductController(BrowseProducts browse, PublishProduct publish) {
        this.browse = browse;
        this.publish = publish;
    }

    /** E-10: newest first, then by id; page and limit as D-C24. Filters arrive with HU-CAT-006. */
    @PublicEndpoint
    @GetMapping
    public PageView<ProductView> list(@RequestParam Map<String, String> query) {
        query.keySet().stream().filter(name -> !LIST_PARAMETERS.contains(name)).findFirst()
                .ifPresent(name -> {
                    throw new InvalidParameterException(name, "Parámetro no permitido.");
                });
        int page = number(query, "page", PageRequest.DEFAULT_PAGE, 1, Integer.MAX_VALUE,
                "Debe ser un número entero mayor o igual a 1.");
        int limit = number(query, "limit", PageRequest.DEFAULT_LIMIT, 1, PageRequest.MAX_LIMIT,
                "Debe ser un número entero entre 1 y " + PageRequest.MAX_LIMIT + ".");
        return PageView.of(browse.list(new PageRequest(page, limit)), ProductView::of);
    }

    /** E-11. A deleted or unknown product is {@code 404 PRODUCT_NOT_FOUND}. */
    @PublicEndpoint
    @GetMapping("/{id}")
    public ProductView get(@PathVariable UUID id) {
        return ProductView.of(browse.get(id));
    }

    /**
     * E-13: {@code 201} with {@code Location}, or {@code 200} with the original product when the
     * producer repeats the {@code Idempotency-Key} (D-C35). The producer is the token's subject.
     */
    @RequiredRole("PRODUCER")
    @PostMapping
    public ResponseEntity<ProductView> publish(@RequestAttribute(Caller.ATTRIBUTE) Caller caller,
                                               @RequestHeader(IDEMPOTENCY_KEY) String idempotencyKey,
                                               @Valid @RequestBody ProductWrite body) {
        if (idempotencyKey.length() < KEY_MIN || idempotencyKey.length() > KEY_MAX) {
            throw new InvalidParameterException(IDEMPOTENCY_KEY,
                    "Debe tener entre " + KEY_MIN + " y " + KEY_MAX + " caracteres.");
        }
        PublishProduct.Publication publication = publish.publish(new PublishProduct.Command(
                caller.userId(), caller.name(), idempotencyKey, body.name(), body.category(), body.unit(),
                body.quantity(), body.priceCents(), body.municipality()));
        ProductView view = ProductView.of(publication.product());
        if (!publication.created()) {
            return ResponseEntity.ok(view);
        }
        return ResponseEntity.created(URI.create(PRODUCTS + "/" + view.id())).body(view);
    }

    private static int number(Map<String, String> query, String name, int fallback, int min, int max,
                              String message) {
        String raw = query.get(name);
        if (raw == null) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < min || value > max) {
                throw new InvalidParameterException(name, message);
            }
            return value;
        } catch (NumberFormatException notANumber) {
            throw new InvalidParameterException(name, message);
        }
    }
}
