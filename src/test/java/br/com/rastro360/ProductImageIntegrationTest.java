package br.com.rastro360;

import br.com.rastro360.ApiModels.Product;
import br.com.rastro360.ApiModels.ProductRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductImageIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private TrackingService trackingService;
    @Autowired private JdbcTemplate jdbc;
    private UUID productId;

    @BeforeEach
    void product() {
        Product product = trackingService.addProduct(new ProductRequest(
                "PHOTO-" + UUID.randomUUID().toString().substring(0, 8),
                "Produto com fotografia", "un"));
        productId = product.id();
    }

    private byte[] imageBytes(String format) throws Exception {
        var image = new BufferedImage(36, 24,
                "png".equalsIgnoreCase(format) ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0xff008577);
        var output = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, format, output)).isTrue();
        return output.toByteArray();
    }

    @Test
    void existingProductWithoutPhotoReturns404() throws Exception {
        mvc.perform(get("/api/products/" + productId + "/image")
                .with(httpBasic("operador", "dev12345")))
                .andExpect(status().isNotFound());
    }

    @Test
    void uploadJpegNormalizesToPngAndCanReadIt() throws Exception {
        mvc.perform(multipart("/api/products/" + productId + "/image")
                .file(new MockMultipartFile("file", "example.jpg", "image/jpeg", imageBytes("jpeg")))
                .with(httpBasic("operador", "dev12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(productId.toString()));

        byte[] png = mvc.perform(get("/api/products/" + productId + "/image")
                        .with(httpBasic("operador", "dev12345")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.IMAGE_PNG_VALUE))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsByteArray();

        var image = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(image).isNotNull();
        assertThat(image.getWidth()).isEqualTo(36);
        assertThat(image.getHeight()).isEqualTo(24);

        // The image is stored in the same database as the product.
        Integer photos = jdbc.queryForObject(
                "SELECT COUNT(*) FROM product_image WHERE product_id = ?", Integer.class, productId);
        assertThat(photos).isEqualTo(1);
    }

    @Test
    void secondUploadReplacesPhotoNotProductOrUnitData() throws Exception {
        var first = new MockMultipartFile("file", "old.png", "image/png", imageBytes("png"));
        var second = new MockMultipartFile("file", "new.jpeg", "image/jpeg", imageBytes("jpeg"));

        mvc.perform(multipart("/api/products/" + productId + "/image").file(first)
                .with(httpBasic("operador", "dev12345"))).andExpect(status().isOk());
        mvc.perform(multipart("/api/products/" + productId + "/image").file(second)
                .with(httpBasic("operador", "dev12345"))).andExpect(status().isOk());

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM product_image WHERE product_id = ?", Integer.class, productId);
        assertThat(count).isEqualTo(1);
        assertThat(trackingService.products().stream().anyMatch(p -> p.id().equals(productId))).isTrue();
    }

    @Test
    void rejectsFakeImageAndUnsupportedMimeType() throws Exception {
        mvc.perform(multipart("/api/products/" + productId + "/image")
                        .file(new MockMultipartFile("file", "fake.png", "image/png",
                                "not a real PNG file".getBytes()))
                        .with(httpBasic("operador", "dev12345")))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/products/" + productId + "/image")
                        .file(new MockMultipartFile("file", "program.html", "text/html",
                                "<script>alert(1)</script>".getBytes()))
                        .with(httpBasic("operador", "dev12345")))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void unknownProductAndMissingAuthenticationAreRejected() throws Exception {
        var file = new MockMultipartFile("file", "sample.png", "image/png", imageBytes("png"));
        mvc.perform(multipart("/api/products/" + UUID.randomUUID() + "/image")
                        .file(file).with(httpBasic("operador", "dev12345")))
                .andExpect(status().isNotFound());
        mvc.perform(multipart("/api/products/" + productId + "/image")
                        .file(file))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/products/" + productId + "/image"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicTrackingPageStaysAvailableWhileImagesStayProtected() throws Exception {
        mvc.perform(get("/tracking.html")).andExpect(status().isOk());
        mvc.perform(get("/label.html")).andExpect(status().isUnauthorized());
    }
}
