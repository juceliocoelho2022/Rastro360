package br.com.rastro360;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ApiModels {
    private ApiModels() { }

    public record Product(UUID id, String sku, String name, String unit) { }
    public record Location(UUID id, String code, String name, String kind) { }
    public record Shipment(UUID id, String reference, String truckPlate, String driverName,
                           String customerName, String customerAddress, String status,
                           Instant createdAt, Instant departedAt, Instant arrivedAt,
                           Instant deliveredAt, String receivedBy) { }
    public record TrackingUnit(UUID id, String trackingCode, UUID productId, String productName,
                               String productSku, String lot, int quantity, String status,
                               UUID locationId, String locationName, UUID shipmentId,
                               String shipmentReference, Instant createdAt) { }
    public record TrackingEvent(UUID id, UUID unitId, String type, UUID fromLocationId,
                                UUID toLocationId, UUID shipmentId, String actor,
                                String details, Instant occurredAt) { }
    public record UnitDetail(TrackingUnit unit, List<TrackingEvent> events) { }
    public record ShipmentDetail(Shipment shipment, List<TrackingUnit> units) { }
    public record PublicEvent(String type, Instant occurredAt, String description) { }
    public record PublicTracking(String trackingCode, String productName, String lot,
                                 int quantity, String status, List<PublicEvent> events) { }
    public record Dashboard(long products, long units, long openShipments, long deliveredShipments,
                            long inTransitUnits, long deliveredUnits) { }

    public record ProductRequest(@NotBlank @Size(max = 60) String sku,
                                 @NotBlank @Size(max = 180) String name,
                                 @NotBlank @Size(max = 20) String unit) { }
    public record LocationRequest(@NotBlank @Size(max = 40) String code,
                                  @NotBlank @Size(max = 160) String name,
                                  @NotBlank String kind) { }
    public record ProduceRequest(@NotNull UUID productId, @NotBlank @Size(max = 80) String lot,
                                 @Min(1) int quantity, @NotNull UUID locationId) { }
    public record MoveRequest(@NotNull UUID destinationLocationId,
                              @Size(max = 400) String details) { }
    public record ShipmentRequest(@NotBlank @Size(max = 20) String truckPlate,
                                  @NotBlank @Size(max = 150) String driverName,
                                  @NotBlank @Size(max = 180) String customerName,
                                  @NotBlank @Size(max = 350) String customerAddress) { }
    public record DeliverRequest(@NotBlank @Size(max = 150) String receivedBy) { }
}
