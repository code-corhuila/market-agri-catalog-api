package co.edu.corhuila.marketagri.catalog.domain.model;

/** A photo that is not JPG, PNG or WebP by its signature (D-C30, {@code 415 UNSUPPORTED_MEDIA_TYPE}). */
public final class UnsupportedImageException extends DomainException {

    public UnsupportedImageException() {
        super("UNSUPPORTED_MEDIA_TYPE", "the file is not a JPG, PNG or WebP image");
    }
}
