package co.edu.corhuila.marketagri.catalog.domain.model;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The name of a stored photo (D-C30): a random UUID as 32 hex characters plus the extension of its
 * format, never the client's file name. Only this shape is a key, so a key can never point outside
 * the storage ({@code ..}, {@code /}). The product shows it as {@code photoUrl = "/media/{key}"} (D-C8).
 */
public record PhotoKey(String value) {

    private static final Pattern SHAPE = Pattern.compile("[0-9a-f]{32}\\.([a-z]+)");
    private static final String MEDIA_PATH = "/media/";

    public PhotoKey {
        Objects.requireNonNull(value, "value");
        if (parsedFormat(value).isEmpty()) {
            throw new IllegalArgumentException("not a photo key: " + value);
        }
    }

    /** A new key for a photo of this format. */
    public static PhotoKey random(ImageFormat format) {
        return new PhotoKey(UUID.randomUUID().toString().replace("-", "") + "." + format.extension());
    }

    /** The key, when the text has its shape; empty otherwise (a request for anything else is a 404). */
    public static Optional<PhotoKey> parse(String text) {
        return text == null || parsedFormat(text).isEmpty() ? Optional.empty() : Optional.of(new PhotoKey(text));
    }

    /** The key of a {@code photoUrl}; empty when the URL is not {@code /media/} plus a key. */
    public static Optional<PhotoKey> fromUrl(String url) {
        return url != null && url.startsWith(MEDIA_PATH) ? parse(url.substring(MEDIA_PATH.length())) : Optional.empty();
    }

    public ImageFormat format() {
        return parsedFormat(value).orElseThrow();
    }

    public String url() {
        return MEDIA_PATH + value;
    }

    private static Optional<ImageFormat> parsedFormat(String text) {
        Matcher matcher = SHAPE.matcher(text);
        return matcher.matches() ? ImageFormat.ofExtension(matcher.group(1)) : Optional.empty();
    }
}
