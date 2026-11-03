package co.edu.corhuila.marketagri.catalog.application.port.out;

import co.edu.corhuila.marketagri.catalog.domain.model.PhotoKey;
import java.util.Optional;

/** Where the binaries of the photos live, by their key (D-C30). */
public interface PhotoStorage {

    void save(PhotoKey key, byte[] content);

    Optional<byte[]> load(PhotoKey key);

    /**
     * Never throws: a binary that cannot be deleted is only an orphan, which is harmless (E-17),
     * while failing here would answer an error for a change that was already committed.
     */
    void delete(PhotoKey key);
}
