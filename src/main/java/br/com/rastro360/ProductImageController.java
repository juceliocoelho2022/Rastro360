package br.com.rastro360;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
public class ProductImageController {
    private final ProductImageService photos;

    public ProductImageController(ProductImageService photos) {
        this.photos = photos;
    }

    @PostMapping(value = "/{productId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> upload(
            @PathVariable UUID productId,
            @RequestPart("file") MultipartFile file) {
        photos.upload(productId, file);
        return ResponseEntity.status(HttpStatus.OK).body(Map.of(
                "productId", productId.toString(),
                "imageUrl", "/api/products/" + productId + "/image"));
    }

    @GetMapping(value = "/{productId}/image", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> image(@PathVariable UUID productId) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noStore())
                .body(photos.image(productId));
    }
}
