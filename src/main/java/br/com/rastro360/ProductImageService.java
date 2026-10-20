package br.com.rastro360;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * One photo per product. Normalized PNG bytes are stored in the same database
 * as the product and therefore included in the existing H2 database backup.
 * User-supplied file names and EXIF metadata are never persisted.
 */
@Service
public class ProductImageService {
    private static final long MAX_UPLOAD_BYTES = 5L * 1024 * 1024;
    private static final long MAX_INPUT_PIXELS = 24_000_000L;
    private static final int MAX_OUTPUT_EDGE = 1000;
    private final JdbcTemplate jdbc;

    public ProductImageService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public byte[] upload(UUID productId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione uma imagem JPG ou PNG.");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "A imagem deve ter até 5 MB.");
        }
        String mime = file.getContentType();
        if (!"image/png".equalsIgnoreCase(mime) && !"image/jpeg".equalsIgnoreCase(mime)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Apenas arquivos JPG e PNG são aceitos.");
        }

        // Lock this product to serialize concurrent photo replacements.
        List<String> product = jdbc.query("SELECT id FROM product WHERE id = ? FOR UPDATE",
                (rs, row) -> rs.getString(1), productId);
        if (product.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado.");
        }

        byte[] normalized = normalizePng(file);
        Timestamp now = Timestamp.from(Instant.now());
        int updated = jdbc.update(
                "UPDATE product_image SET image_data = ?, updated_at = ? WHERE product_id = ?",
                normalized, now, productId);
        if (updated == 0) {
            jdbc.update(
                    "INSERT INTO product_image (product_id, image_data, updated_at) VALUES (?, ?, ?)",
                    productId, normalized, now);
        }
        return normalized;
    }

    @Transactional(readOnly = true)
    public byte[] image(UUID productId) {
        List<byte[]> rows = jdbc.query(
                "SELECT image_data FROM product_image WHERE product_id = ?",
                (rs, row) -> rs.getBytes(1), productId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto sem imagem cadastrada.");
        }
        return rows.getFirst();
    }

    private byte[] normalizePng(MultipartFile file) {
        try {
            byte[] inputBytes = file.getBytes();
            if (inputBytes.length > MAX_UPLOAD_BYTES) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "A imagem deve ter até 5 MB.");
            }
            try (ImageInputStream imageInput = ImageIO.createImageInputStream(
                    new ByteArrayInputStream(inputBytes))) {
                if (imageInput == null) {
                    throw invalidImage();
                }
                Iterator<ImageReader> available = ImageIO.getImageReaders(imageInput);
                if (!available.hasNext()) {
                    throw invalidImage();
                }
                ImageReader reader = available.next();
                try {
                    reader.setInput(imageInput);
                    String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                    if (!format.equals("png") && !format.equals("jpeg") && !format.equals("jpg")) {
                        throw invalidImage();
                    }
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    if (width < 1 || height < 1 || ((long) width * height) > MAX_INPUT_PIXELS) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "Imagem grande demais. Use uma foto de até 24 megapixels.");
                    }
                    BufferedImage decoded = reader.read(0);
                    if (decoded == null) {
                        throw invalidImage();
                    }
                    double scale = Math.min(1.0,
                            MAX_OUTPUT_EDGE / (double) Math.max(width, height));
                    int newWidth = Math.max(1, (int) Math.round(width * scale));
                    int newHeight = Math.max(1, (int) Math.round(height * scale));
                    BufferedImage resized = new BufferedImage(
                            newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = resized.createGraphics();
                    try {
                        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                        g.drawImage(decoded, 0, 0, newWidth, newHeight, null);
                    } finally {
                        g.dispose();
                    }
                    ByteArrayOutputStream result = new ByteArrayOutputStream();
                    if (!ImageIO.write(resized, "png", result)) {
                        throw new IllegalStateException("Encoder PNG indisponível.");
                    }
                    // Re-encoding intentionally removes EXIF and other source metadata.
                    return result.toByteArray();
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException | IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Arquivo de imagem inválido.", error);
        }
    }

    private ResponseStatusException invalidImage() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "O arquivo não contém uma imagem JPG ou PNG válida.");
    }
}
