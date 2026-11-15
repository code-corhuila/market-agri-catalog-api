package co.edu.corhuila.marketagri.catalog.adapter.in.http;

import co.edu.corhuila.marketagri.catalog.application.port.in.BrowseProducts;
import co.edu.corhuila.marketagri.catalog.application.port.in.ReleaseStock;
import co.edu.corhuila.marketagri.catalog.application.port.in.ReserveStock;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal API of catalog (api-contract §4.2): I-02, I-03 and I-04. Only a {@code SERVICE} token
 * gets here ({@link AuthInterceptor}, D-C27), and the gateway never routes {@code /internal/**}.
 */
@RestController
@RequestMapping("/internal/v1/catalog")
public class InternalCatalogController {

    private final BrowseProducts browse;
    private final ReserveStock reserve;
    private final ReleaseStock release;

    public InternalCatalogController(BrowseProducts browse, ReserveStock reserve, ReleaseStock release) {
        this.browse = browse;
        this.reserve = reserve;
        this.release = release;
    }

    /** I-02 (chat, E-30): the same {@code ProductView} as E-11. */
    @GetMapping("/products/{id}")
    public ProductView product(@PathVariable UUID id) {
        return ProductView.of(browse.get(id));
    }

    /** I-03 (checkout saga): {@code 201} the first time, {@code 200} with the same body on a retry. */
    @PostMapping("/products/{id}/reservations")
    public ResponseEntity<ReservationView> reserve(@PathVariable UUID id, @Valid @RequestBody ReserveRequest body) {
        ReserveStock.Reservation result = reserve.reserve(id, body.transactionId(), body.quantity());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ReservationView.of(result.reservation()));
    }

    /** I-04 (compensation of the saga): always {@code 204}. */
    @DeleteMapping("/reservations/{transactionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(@PathVariable UUID transactionId) {
        release.release(transactionId);
    }
}
