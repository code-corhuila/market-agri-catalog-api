package co.edu.corhuila.marketagri.catalog.adapter.out.storage;

import co.edu.corhuila.marketagri.catalog.application.port.out.PhotoStorage;
import co.edu.corhuila.marketagri.catalog.domain.model.PhotoKey;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link PhotoStorage} on a folder: one file per key. A {@link PhotoKey} has no separators, so a
 * key always names a file inside the folder. A photo is written to a temporary file and then
 * renamed, so a reader never sees half a photo.
 */
public final class FileSystemPhotoStorage implements PhotoStorage {

    private static final Logger LOG = LoggerFactory.getLogger(FileSystemPhotoStorage.class);

    private final Path folder;

    public FileSystemPhotoStorage(Path folder) {
        this.folder = folder.toAbsolutePath().normalize();
    }

    @Override
    public void save(PhotoKey key, byte[] content) {
        Path partial = null;
        try {
            Files.createDirectories(folder);
            partial = Files.createTempFile(folder, "upload-", ".part");
            Files.write(partial, content);
            Files.move(partial, fileOf(key), StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException cannotWrite) {
            throw new UncheckedIOException("could not store photo " + key.value(), cannotWrite);
        } finally {
            deleteQuietly(partial);
        }
    }

    @Override
    public Optional<byte[]> load(PhotoKey key) {
        Path file = fileOf(key);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(file));
        } catch (IOException cannotRead) {
            throw new UncheckedIOException("could not read photo " + key.value(), cannotRead);
        }
    }

    @Override
    public void delete(PhotoKey key) {
        try {
            Files.deleteIfExists(fileOf(key));
        } catch (IOException cannotDelete) {
            LOG.warn("Photo {} left as an orphan: {}", key.value(), cannotDelete.getMessage());
        }
    }

    private Path fileOf(PhotoKey key) {
        return folder.resolve(key.value());
    }

    /** After a successful move the temporary file no longer exists: nothing to do. */
    private static void deleteQuietly(Path partial) {
        if (partial == null) {
            return;
        }
        try {
            Files.deleteIfExists(partial);
        } catch (IOException ignored) {
            LOG.warn("Temporary upload {} left behind", partial.getFileName());
        }
    }
}
