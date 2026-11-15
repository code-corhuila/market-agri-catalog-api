package co.edu.corhuila.marketagri.catalog.application.port.in;

import co.edu.corhuila.marketagri.catalog.domain.model.Product;
import java.util.UUID;

/**
 * The owner sets, replaces or removes the photo of a product (E-16, E-17; HU-CAT-001, D-C8). Errors
 * in the order of the contract: {@code PRODUCT_NOT_FOUND}, {@code NOT_PRODUCT_OWNER}, then
 * {@code UNSUPPORTED_MEDIA_TYPE}.
 */
public interface ManageProductPhoto {

    /** The product with its new photo; the previous binary is deleted after the commit. */
    Product setPhoto(UUID productId, UUID producerId, byte[] content);

    /** Also when it had no photo: then nothing changes. */
    void removePhoto(UUID productId, UUID producerId);
}
