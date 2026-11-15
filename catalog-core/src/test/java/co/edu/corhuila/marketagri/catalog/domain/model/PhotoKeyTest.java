package co.edu.corhuila.marketagri.catalog.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * D-C30: the key of a stored photo is a random UUID (32 hex characters) plus the extension, and the
 * product keeps it as {@code photoUrl = "/media/{key}"} (D-C8). Anything else is not a key: it can
 * never reach the storage (no {@code ..}, no {@code /}).
 */
class PhotoKeyTest {

    private static final String KEY = "0123456789abcdef0123456789abcdef.png";

    @Test
    void aNewKeyIsRandomHexPlusTheExtensionOfTheFormat() {
        PhotoKey first = PhotoKey.random(ImageFormat.WEBP);
        PhotoKey second = PhotoKey.random(ImageFormat.WEBP);

        assertThat(first.value()).matches("[0-9a-f]{32}\\.webp");
        assertThat(first.value()).isNotEqualTo(second.value());
        assertThat(first.format()).isEqualTo(ImageFormat.WEBP);
    }

    @Test
    void theUrlIsTheMediaPathOfTheKey() {
        PhotoKey key = PhotoKey.parse(KEY).orElseThrow();

        assertThat(key.url()).isEqualTo("/media/" + KEY);
        assertThat(PhotoKey.fromUrl("/media/" + KEY)).isEqualTo(Optional.of(key));
        assertThat(key.format()).isEqualTo(ImageFormat.PNG);
    }

    @Test
    void onlyAWellFormedKeyIsParsed() {
        assertThat(PhotoKey.parse("0123456789ABCDEF0123456789ABCDEF.png")).isEqualTo(Optional.empty());
        assertThat(PhotoKey.parse("0123456789abcdef0123456789abcdef.gif")).isEqualTo(Optional.empty());
        assertThat(PhotoKey.parse("0123456789abcdef.png")).isEqualTo(Optional.empty());
        assertThat(PhotoKey.parse("../../etc/passwd")).isEqualTo(Optional.empty());
        assertThat(PhotoKey.parse("0123456789abcdef0123456789abcdef.png/..")).isEqualTo(Optional.empty());
        assertThat(PhotoKey.parse("")).isEqualTo(Optional.empty());
        assertThat(PhotoKey.parse(null)).isEqualTo(Optional.empty());
    }

    @Test
    void aUrlThatIsNotTheMediaPathOfAKeyHasNoKey() {
        assertThat(PhotoKey.fromUrl("https://example.com/" + KEY)).isEqualTo(Optional.empty());
        assertThat(PhotoKey.fromUrl("/media/photo.jpg")).isEqualTo(Optional.empty());
        assertThat(PhotoKey.fromUrl(null)).isEqualTo(Optional.empty());
    }

    @Test
    void aKeyCannotBeBuiltFromAnythingElse() {
        assertThatThrownBy(() -> new PhotoKey("../catalog.png")).isInstanceOf(IllegalArgumentException.class);
    }
}
