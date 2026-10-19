package br.com.rastro360;

import br.com.rastro360.ApiModels.PublicTracking;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
public class PublicTrackingController {
    private final TrackingService service;

    public PublicTrackingController(TrackingService service) {
        this.service = service;
    }

    @GetMapping("/track/{code}")
    public PublicTracking track(@PathVariable String code) {
        return service.publicTracking(code);
    }
}
