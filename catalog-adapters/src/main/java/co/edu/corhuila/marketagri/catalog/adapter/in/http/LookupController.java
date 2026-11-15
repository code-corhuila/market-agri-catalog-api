package co.edu.corhuila.marketagri.catalog.adapter.in.http;

import co.edu.corhuila.marketagri.catalog.domain.model.ProductCategory;
import co.edu.corhuila.marketagri.catalog.domain.model.ProductUnit;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * E-19 and E-20 (D-C6): the categories and the units with their labels, anonymous, as a plain array
 * in the order of the enums (the order of the contract). The labels are display text, so they live
 * here and not in the core; the {@code switch} without {@code default} does not compile when a new
 * code has no label.
 */
@PublicEndpoint
@RestController
public class LookupController {

    private static final List<LookupView> CATEGORIES = Arrays.stream(ProductCategory.values())
            .map(category -> new LookupView(category.name(), label(category))).toList();
    private static final List<LookupView> UNITS = Arrays.stream(ProductUnit.values())
            .map(unit -> new LookupView(unit.name(), label(unit))).toList();

    @GetMapping("/api/v1/catalog/categories")
    public List<LookupView> categories() {
        return CATEGORIES;
    }

    @GetMapping("/api/v1/catalog/units")
    public List<LookupView> units() {
        return UNITS;
    }

    private static String label(ProductCategory category) {
        return switch (category) {
            case FRUTAS -> "Frutas";
            case VERDURAS -> "Verduras";
            case HORTALIZAS -> "Hortalizas";
            case TUBERCULOS -> "Tubérculos";
            case GRANOS_Y_CEREALES -> "Granos y cereales";
            case CAFE -> "Café";
            case CACAO -> "Cacao";
            case LACTEOS -> "Lácteos";
            case HIERBAS_AROMATICAS -> "Hierbas aromáticas";
            case OTROS -> "Otros";
        };
    }

    private static String label(ProductUnit unit) {
        return switch (unit) {
            case KILOGRAMO -> "Kilogramo";
            case LIBRA -> "Libra";
            case ARROBA -> "Arroba";
            case BULTO -> "Bulto";
            case CANASTA -> "Canasta";
            case CAJA -> "Caja";
            case DOCENA -> "Docena";
            case MANOJO -> "Manojo";
            case LITRO -> "Litro";
            case UNIDAD -> "Unidad";
        };
    }
}
