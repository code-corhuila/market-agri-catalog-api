package co.edu.corhuila.marketagri.catalog.adapter.in.http;

import co.edu.corhuila.marketagri.catalog.application.port.in.ManageProductPhoto;
import co.edu.corhuila.marketagri.catalog.application.port.in.ServePhoto;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * The photo of a product (HU-CAT-001, D-C8, D-C30): its owner sets or removes it (E-16, E-17), and
 * anyone reads it by its key (E-62), because an {@code <img>} cannot send a token. The 5 MB ceiling
 * is the multipart limit of application.yml ({@code 413}).
 */
@RestController
public class ProductPhotoController {

    private static final String PHOTO = "/api/v1/catalog/products/{id}/photo";
    private static final String FILE_PART = "file";
    /** E-62: a key never changes its bytes, so the browser may keep them for a day. */
    private static final String ONE_DAY = "public, max-age=86400";

    private final ManageProductPhoto photos;
    private final ServePhoto media;

    public ProductPhotoController(ManageProductPhoto photos, ServePhoto media) {
        this.photos = photos;
        this.media = media;
    }

    /** E-16: {@code 200} with the product and its new {@code photoUrl}. */
    @RequiredRole("PRODUCER")
    @PostMapping(PHOTO)
    public ProductView setPhoto(@RequestAttribute(Caller.ATTRIBUTE) Caller caller, @PathVariable UUID id,
                                @RequestPart(FILE_PART) MultipartFile file) throws IOException {
        return ProductView.of(photos.setPhoto(id, caller.userId(), file.getBytes()));
    }

    /** E-17: {@code 204}, also when the product had no photo. */
    @RequiredRole("PRODUCER")
    @DeleteMapping(PHOTO)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removePhoto(@RequestAttribute(Caller.ATTRIBUTE) Caller caller, @PathVariable UUID id) {
        photos.removePhoto(id, caller.userId());
    }

    /** E-62: the bytes with their type; an unknown key is the standard {@code 404 NOT_FOUND}. */
    @PublicEndpoint
    @GetMapping("/media/{key}")
    public ResponseEntity<byte[]> photo(@PathVariable String key) {
        ServePhoto.Photo photo = media.find(key).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.mediaType()))
                .header(HttpHeaders.CACHE_CONTROL, ONE_DAY)
                .body(photo.content());
    }
}
