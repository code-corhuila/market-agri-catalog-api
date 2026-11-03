package co.edu.corhuila.marketagri.catalog.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** D-C30: JPG, PNG and WebP, recognised by their file signature (magic bytes), never by a name. */
class ImageFormatTest {

    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};
    static final byte[] WEBP = {'R', 'I', 'F', 'F', 0x24, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '};

    @Test
    void recognisesTheThreeFormatsByTheirSignature() {
        assertThat(ImageFormat.detect(JPEG)).isEqualTo(Optional.of(ImageFormat.JPEG));
        assertThat(ImageFormat.detect(PNG)).isEqualTo(Optional.of(ImageFormat.PNG));
        assertThat(ImageFormat.detect(WEBP)).isEqualTo(Optional.of(ImageFormat.WEBP));
    }

    @Test
    void eachFormatHasItsExtensionAndMediaType() {
        assertThat(ImageFormat.JPEG.extension()).isEqualTo("jpg");
        assertThat(ImageFormat.JPEG.mediaType()).isEqualTo("image/jpeg");
        assertThat(ImageFormat.PNG.extension()).isEqualTo("png");
        assertThat(ImageFormat.PNG.mediaType()).isEqualTo("image/png");
        assertThat(ImageFormat.WEBP.extension()).isEqualTo("webp");
        assertThat(ImageFormat.WEBP.mediaType()).isEqualTo("image/webp");
    }

    @Test
    void aPdfRenamedAsJpgIsNotAnImage() {
        assertThat(ImageFormat.detect("%PDF-1.7\n%âãÏÓ".getBytes(StandardCharsets.ISO_8859_1))).isEqualTo(Optional.empty());
    }

    @Test
    void aRiffFileThatIsNotWebpIsNotAnImage() {
        byte[] wave = {'R', 'I', 'F', 'F', 0x24, 0, 0, 0, 'W', 'A', 'V', 'E'};

        assertThat(ImageFormat.detect(wave)).isEqualTo(Optional.empty());
    }

    @Test
    void anEmptyOrTruncatedFileIsNotAnImage() {
        assertThat(ImageFormat.detect(new byte[0])).isEqualTo(Optional.empty());
        assertThat(ImageFormat.detect(new byte[] {(byte) 0xFF, (byte) 0xD8})).isEqualTo(Optional.empty());
        assertThat(ImageFormat.detect(new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E'})).isEqualTo(Optional.empty());
    }
}
