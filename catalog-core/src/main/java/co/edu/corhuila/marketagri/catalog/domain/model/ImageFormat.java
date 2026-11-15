package co.edu.corhuila.marketagri.catalog.domain.model;

import java.util.Optional;

/**
 * The formats a product photo may have (D-C30). Recognised by the file signature (magic bytes),
 * never by the name or the declared type, which the client controls.
 */
public enum ImageFormat {
    JPEG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
    WEBP("webp", "image/webp");

    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] RIFF = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP_TAG = {'W', 'E', 'B', 'P'};
    /** In a RIFF file the chunk size (4 bytes) comes between "RIFF" and the form type. */
    private static final int RIFF_FORM_TYPE_OFFSET = 8;

    private final String extension;
    private final String mediaType;

    ImageFormat(String extension, String mediaType) {
        this.extension = extension;
        this.mediaType = mediaType;
    }

    public String extension() {
        return extension;
    }

    public String mediaType() {
        return mediaType;
    }

    /** The format of these bytes, or empty when they are not JPG, PNG or WebP. */
    public static Optional<ImageFormat> detect(byte[] content) {
        if (startsWith(content, 0, JPEG_SIGNATURE)) {
            return Optional.of(JPEG);
        }
        if (startsWith(content, 0, PNG_SIGNATURE)) {
            return Optional.of(PNG);
        }
        if (startsWith(content, 0, RIFF) && startsWith(content, RIFF_FORM_TYPE_OFFSET, WEBP_TAG)) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    static Optional<ImageFormat> ofExtension(String extension) {
        for (ImageFormat format : values()) {
            if (format.extension.equals(extension)) {
                return Optional.of(format);
            }
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] content, int offset, byte[] expected) {
        if (content.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (content[offset + i] != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
