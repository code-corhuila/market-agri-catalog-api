package co.edu.corhuila.marketagri.catalog.app;

import co.edu.corhuila.marketagri.catalog.adapter.in.http.Caller;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only routes that need a token: one user route and one internal route. They answer the
 * caller the interceptor left in the request. Test sources only: never in the jar.
 */
@RestController
public class SecuredProbeController {

    @GetMapping("/probe/secured")
    public Map<String, String> user(@RequestAttribute(Caller.ATTRIBUTE) Caller caller) {
        return Map.of("subject", caller.subject(), "role", caller.role());
    }

    @GetMapping("/internal/v1/catalog/probe")
    public Map<String, String> internal(@RequestAttribute(Caller.ATTRIBUTE) Caller caller) {
        return Map.of("subject", caller.subject(), "role", caller.role());
    }
}
