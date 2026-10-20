package br.com.rastro360;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class QrCodeController {
    private final TrackingService trackingService;
    private final QrCodeService qrCodeService;

    public QrCodeController(TrackingService trackingService, QrCodeService qrCodeService) {
        this.trackingService = trackingService;
        this.qrCodeService = qrCodeService;
    }

    @GetMapping(value = "/api/units/{trackingCode}/qr.png",
                produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> qrCode(@PathVariable String trackingCode,
                                         HttpServletRequest request) {
        // Só gera etiquetas para códigos válidos e existentes.
        var unit = trackingService.unitByCode(trackingCode);
        byte[] png = qrCodeService.png(qrCodeService.trackingUrl(unit.trackingCode(), request));

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noStore())
                .body(png);
    }
}
