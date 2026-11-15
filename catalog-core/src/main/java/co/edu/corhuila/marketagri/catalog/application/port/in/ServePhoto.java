package co.edu.corhuila.marketagri.catalog.application.port.in;

import co.edu.corhuila.marketagri.catalog.domain.model.PhotoKey;
import java.util.Optional;

/** Anyone reads a stored photo by its key (E-62, D-C30): an {@code <img>} cannot send a token. */
public interface ServePhoto {

    /** Empty when the key does not exist or is not a key at all. */
    Optional<Photo> find(String key);

    record Photo(PhotoKey key, byte[] content) {

        public String mediaType() {
            return key.format().mediaType();
        }
    }
}
