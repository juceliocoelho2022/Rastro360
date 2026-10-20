package br.com.rastro360;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Cria um QR Code por volume sem modificar o código RT- ou o histórico existente.
 * O QR aponta para a consulta pública, que expõe apenas eventos permitidos.
 */
@Service
public class QrCodeService {
    private static final int SIZE = 320;
    private final String configuredBaseUrl;

    public QrCodeService(@Value("${app.qr.public-base-url:}") String configuredBaseUrl) {
        this.configuredBaseUrl = configuredBaseUrl == null ? "" : configuredBaseUrl.trim();
    }

    public String trackingUrl(String trackingCode, HttpServletRequest request) {
        String baseUrl = configuredBaseUrl.isBlank() ? requestOrigin(request) : configuredBaseUrl;
        baseUrl = baseUrl.replaceAll("/+$", "");
        return baseUrl + "/tracking.html?code="
                + URLEncoder.encode(trackingCode, StandardCharsets.UTF_8);
    }

    private String requestOrigin(HttpServletRequest request) {
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        boolean defaultPort = ("http".equalsIgnoreCase(scheme) && port == 80)
                || ("https".equalsIgnoreCase(scheme) && port == 443);
        return scheme + "://" + host + (defaultPort ? "" : ":" + port);
    }

    public byte[] png(String trackingUrl) {
        try {
            var hints = Map.of(
                    EncodeHintType.CHARACTER_SET, "UTF-8",
                    EncodeHintType.MARGIN, 2,
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            var matrix = new MultiFormatWriter().encode(
                    trackingUrl, BarcodeFormat.QR_CODE, SIZE, SIZE, hints);
            var output = new ByteArrayOutputStream();
            if (!ImageIO.write(MatrixToImageWriter.toBufferedImage(matrix), "PNG", output)) {
                throw new IllegalStateException("O encoder PNG não está disponível.");
            }
            return output.toByteArray();
        } catch (WriterException | IOException exception) {
            throw new IllegalStateException("Não foi possível gerar o QR Code.", exception);
        }
    }
}
