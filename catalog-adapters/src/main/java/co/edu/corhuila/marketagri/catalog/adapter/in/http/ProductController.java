package co.edu.corhuila.marketagri.catalog.adapter.in.http;

import co.edu.corhuila.marketagri.catalog.application.port.in.BrowseProducts;
import co.edu.corhuila.marketagri.catalog.domain.model.PageRequest;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Browsing the catalog (HU-CAT-005): E-10 and E-11, both anonymous (D-C9). */
@RestController
@RequestMapping("/api/v1/catalog/products")
public class ProductController {

    private static final Set<String> LIST_PARAMETERS = Set.of("page", "limit");

    private final BrowseProducts browse;

    public ProductController(BrowseProducts browse) {
        this.browse = browse;
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
