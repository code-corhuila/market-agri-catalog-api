package co.edu.corhuila.marketagri.catalog.adapter.in.http;

/**
 * Who calls, taken from a verified token (authentication.md §2). {@code subject} is the only
 * source of the caller's identity; {@code name} is absent in service tokens.
 */
public record Caller(String subject, String role, String name) {

    /** Request attribute where {@link AuthInterceptor} leaves the caller for the controllers. */
    public static final String ATTRIBUTE = "catalog.caller";

    static final String SERVICE_ROLE = "SERVICE";

    public boolean isService() {
        return SERVICE_ROLE.equals(role);
    }
}
