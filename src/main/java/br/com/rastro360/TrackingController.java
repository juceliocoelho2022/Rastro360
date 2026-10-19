package br.com.rastro360;

import br.com.rastro360.ApiModels.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class TrackingController {
    private final TrackingService service;

    public TrackingController(TrackingService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard() { return service.dashboard(); }

    @GetMapping("/products")
    public List<Product> products() { return service.products(); }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public Product product(@Valid @RequestBody ProductRequest request) {
        return service.addProduct(request);
    }

    @GetMapping("/locations")
    public List<Location> locations() { return service.locations(); }

    @PostMapping("/locations")
    @ResponseStatus(HttpStatus.CREATED)
    public Location location(@Valid @RequestBody LocationRequest request) {
        return service.addLocation(request);
    }

    @GetMapping("/units")
    public List<TrackingUnit> units() { return service.units(); }

    @PostMapping("/units")
    @ResponseStatus(HttpStatus.CREATED)
    public UnitDetail produce(@Valid @RequestBody ProduceRequest request, Principal principal) {
        return service.produce(request, principal.getName());
    }

    @GetMapping("/units/{trackingCode}")
    public UnitDetail detail(@PathVariable String trackingCode) {
        return service.detail(trackingCode);
    }

    @PostMapping("/units/{id}/move")
    public UnitDetail move(@PathVariable UUID id, @Valid @RequestBody MoveRequest request,
                           Principal principal) {
        return service.move(id, request, principal.getName());
    }

    @GetMapping("/shipments")
    public List<Shipment> shipments() { return service.shipments(); }

    @PostMapping("/shipments")
    @ResponseStatus(HttpStatus.CREATED)
    public Shipment shipment(@Valid @RequestBody ShipmentRequest request) {
        return service.addShipment(request);
    }

    @GetMapping("/shipments/{id}")
    public ShipmentDetail shipmentDetail(@PathVariable UUID id) {
        return service.shipmentDetail(id);
    }

    @PostMapping("/shipments/{id}/load/{unitId}")
    public ShipmentDetail load(@PathVariable UUID id, @PathVariable UUID unitId,
                               Principal principal) {
        return service.load(id, unitId, principal.getName());
    }

    @PostMapping("/shipments/{id}/depart")
    public ShipmentDetail depart(@PathVariable UUID id, Principal principal) {
        return service.depart(id, principal.getName());
    }

    @PostMapping("/shipments/{id}/arrive")
    public ShipmentDetail arrive(@PathVariable UUID id, Principal principal) {
        return service.arrive(id, principal.getName());
    }

    @PostMapping("/shipments/{id}/deliver")
    public ShipmentDetail deliver(@PathVariable UUID id, @Valid @RequestBody DeliverRequest request,
                                  Principal principal) {
        return service.deliver(id, request, principal.getName());
    }
}
