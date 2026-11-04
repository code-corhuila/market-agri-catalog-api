package co.edu.corhuila.marketagri.catalog.adapter.in.http;

import co.edu.corhuila.marketagri.catalog.application.port.in.BrowseProducts;
import co.edu.corhuila.marketagri.catalog.application.port.in.EditProduct;
import co.edu.corhuila.marketagri.catalog.application.port.in.PublishProduct;
import co.edu.corhuila.marketagri.catalog.domain.model.MunicipalityKey;
import co.edu.corhuila.marketagri.catalog.domain.model.PageRequest;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductCategory;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductFilter;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductStatus;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Products of the catalog: E-10 with its filters and E-11, anonymous (HU-CAT-005, HU-CAT-006, D-C9),
 * E-12 and E-14, a producer's own products (HU-CAT-002), and E-13, a producer
 * publishing (HU-CAT-001).
 */
@RestController
@RequestMapping("/api/v1/catalog/products")
public class ProductController {

    private static final Set<String> LIST_PARAMETERS =
            Set.of("page", "limit", "category", "municipality", "status");
    /** E-12: the query of E-10 without {@code category} and {@code municipality}. */
    private static final Set<String> MINE_PARAMETERS = Set.of("page", "limit", "status");
    private static final int MUNICIPALITY_MAX = 100;
    private static final String PRODUCTS = "/api/v1/catalog/products";
    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    private static final int KEY_MIN = 8;
    private static final int KEY_MAX = 128;

    private final BrowseProducts browse;
    private final PublishProduct publish;
    private final EditProduct edit;

    public ProductController(BrowseProducts browse, PublishProduct publish, EditProduct edit) {
        this.browse = browse;
        this.publish = publish;
        this.edit = edit;
    }

    /**
     * E-10: newest first, then by id; page and limit as D-C24. {@code category} and {@code status} are
     * exact codes; {@code municipality} is compared without case or accents (D-16). They combine with
     * AND (FR-015), and a combination without products is an empty page.
     */
    @PublicEndpoint
    @GetMapping
    public PageView<ProductView> list(@RequestParam Map<String, String> query) {
        allowOnly(query, LIST_PARAMETERS);
        ProductFilter filter = new ProductFilter(code(query, "category", ProductCategory.class),
                municipality(query.get("municipality")), code(query, "status", ProductStatus.class));
        return PageView.of(browse.list(filter, pageRequest(query)), ProductView::of);
    }

    /**
     * E-12: only the caller's products, both statuses unless {@code status} asks one, in the order of
     * E-10. The literal path wins over {@code /{id}}, so {@code mine} is never read as an id.
     */
    @RequiredRole("PRODUCER")
    @GetMapping("/mine")
    public PageView<ProductView> mine(@RequestAttribute(Caller.ATTRIBUTE) Caller caller,
                                      @RequestParam Map<String, String> query) {
        allowOnly(query, MINE_PARAMETERS);
        ProductFilter filter = ProductFilter.mine(caller.userId(), code(query, "status", ProductStatus.class));
        return PageView.of(browse.list(filter, pageRequest(query)), ProductView::of);
    }

    /**
     * E-14: full replacement by its owner; the status follows the new quantity (FR-013). The shape
     * of the body ({@code 400}) is checked first; then the core answers 404, 403 and the 422 in the
     * order of the contract.
     */
    @RequiredRole("PRODUCER")
    @PutMapping("/{id}")
    public ProductView edit(@RequestAttribute(Caller.ATTRIBUTE) Caller caller, @PathVariable UUID id,
                            @Valid @RequestBody ProductWrite body) {
        return ProductView.of(edit.edit(new EditProduct.Command(id, caller.userId(), body.name(),
                body.category(), body.unit(), body.quantity(), body.priceCents(), body.municipality())));
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

    private static void allowOnly(Map<String, String> query, Set<String> allowed) {
        query.keySet().stream().filter(name -> !allowed.contains(name)).findFirst()
                .ifPresent(name -> {
                    throw new InvalidParameterException(name, "Parámetro no permitido.");
                });
    }

    private static PageRequest pageRequest(Map<String, String> query) {
        int page = number(query, "page", PageRequest.DEFAULT_PAGE, 1, Integer.MAX_VALUE,
                "Debe ser un número entero mayor o igual a 1.");
        int limit = number(query, "limit", PageRequest.DEFAULT_LIMIT, 1, PageRequest.MAX_LIMIT,
                "Debe ser un número entero entre 1 y " + PageRequest.MAX_LIMIT + ".");
        return new PageRequest(page, limit);
    }

    /** An enum of the contract, exactly as written ({@code CAFE}, not {@code cafe}); absent is {@code null}. */
    private static <E extends Enum<E>> E code(Map<String, String> query, String name, Class<E> type) {
        String raw = query.get(name);
        if (raw == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, raw);
        } catch (IllegalArgumentException unknown) {
            throw new InvalidParameterException(name, "Valor no permitido.");
        }
    }

    /** 1–100 characters after trim, like {@code ProductWrite}, and at least one letter or digit for a key. */
    private static MunicipalityKey municipality(String raw) {
        if (raw == null) {
            return null;
        }
        String municipality = raw.trim();
        if (municipality.isEmpty() || municipality.length() > MUNICIPALITY_MAX) {
            throw new InvalidParameterException("municipality",
                    "Debe tener entre 1 y " + MUNICIPALITY_MAX + " caracteres.");
        }
        try {
            return MunicipalityKey.of(municipality);
        } catch (IllegalArgumentException withoutLetters) {
            throw new InvalidParameterException("municipality", "Debe tener letras o números.");
        }
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
