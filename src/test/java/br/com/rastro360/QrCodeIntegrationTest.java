package br.com.rastro360;

import br.com.rastro360.ApiModels.*;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QrCodeIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private TrackingService trackingService;

    private String trackingCode;

    @BeforeEach
    void setup() {
        Product product = trackingService.addProduct(new ProductRequest(
                "QR-" + UUID.randomUUID().toString().substring(0, 8), "Produto com QR", "un"));
        Location production = trackingService.locations().stream()
                .filter(location -> location.kind().equals("PRODUCAO"))
                .findFirst().orElseThrow();
        UnitDetail detail = trackingService.produce(new ProduceRequest(
                product.id(), "QR-LOT-001", 5, production.id()), "operador");
        trackingCode = detail.unit().trackingCode();
    }

    @Test
    void pngIsValidAndDecodesToPublicTrackingLink() throws Exception {
        byte[] png = mvc.perform(get("/api/units/" + trackingCode + "/qr.png")
                        .with(httpBasic("operador", "dev12345"))
                        .with(request -> {
                            request.setServerName("localhost");
                            request.setServerPort(8081);
                            request.setScheme("http");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(png).startsWith((byte)0x89, (byte)0x50, (byte)0x4E, (byte)0x47);
        var image = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(image).isNotNull();
        assertThat(image.getWidth()).isEqualTo(320);
        assertThat(image.getHeight()).isEqualTo(320);
        var bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
        String decoded = new MultiFormatReader().decode(bitmap).getText();
        assertThat(decoded).isEqualTo("http://localhost:8081/tracking.html?code=" + trackingCode);
    }

    @Test
    void qrPngAndOperationalPagesRequireAuthentication() throws Exception {
        mvc.perform(get("/api/units/" + trackingCode + "/qr.png"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/label.html")).andExpect(status().isUnauthorized());
        mvc.perform(get("/scanner.html")).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownVolumeCannotGenerateQrCode() throws Exception {
        mvc.perform(get("/api/units/RT-00000000000000000000000000000000/qr.png")
                        .with(httpBasic("operador", "dev12345")))
                .andExpect(status().isNotFound());
    }

    @Test
    void publicTrackingRemainsAccessibleWithoutAuthentication() throws Exception {
        mvc.perform(get("/tracking.html?code=" + trackingCode)).andExpect(status().isOk());
        mvc.perform(get("/api/public/track/" + trackingCode))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                        "$.trackingCode").value(trackingCode));
    }
}
