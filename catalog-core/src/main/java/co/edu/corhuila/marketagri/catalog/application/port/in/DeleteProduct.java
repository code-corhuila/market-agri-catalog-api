package co.edu.corhuila.marketagri.catalog.application.port.in;

import java.util.UUID;

/** The owner deletes a product (E-18, HU-CAT-003): logical and terminal (D-C5). */
public interface DeleteProduct {

    /** In the order of the contract: {@code PRODUCT_NOT_FOUND} (also the second time), {@code NOT_PRODUCT_OWNER}. */
    void delete(UUID productId, UUID producerId);
}
