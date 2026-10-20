package br.com.rastro360;

import br.com.rastro360.ApiModels.PublicTracking;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/public")
public class PublicTrackingController {
    private final TrackingService service;

    public PublicTrackingController(TrackingService service) {
        this.service = service;
    }

    @GetMapping("/track/{code}")
    public ResponseEntity<PublicTracking> track(@PathVariable String code) {
        if (code == null || !code.matches("(?i)RT-[0-9a-f]{32}")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Código não encontrado.");
        }
        return ResponseEntity.ok()
                // Avoid stale browser/proxy copies of shipment statuses.
                .cacheControl(CacheControl.noStore())
                .header("X-Robots-Tag", "noindex, nofollow")
                .header("Referrer-Policy", "no-referrer")
                .body(service.publicTracking(code.toUpperCase(java.util.Locale.ROOT)));
    }
}
