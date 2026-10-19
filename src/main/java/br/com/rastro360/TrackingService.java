package br.com.rastro360;

import br.com.rastro360.ApiModels.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class TrackingService {
    private final JdbcTemplate jdbc;

    private static final String UNIT_SELECT = """
            SELECT u.*, p.name AS product_name, p.sku AS product_sku,
                   l.name AS location_name, s.reference AS shipment_reference
            FROM tracking_unit u
            JOIN product p ON p.id = u.product_id
            LEFT JOIN location l ON l.id = u.location_id
            LEFT JOIN shipment s ON s.id = u.shipment_id
            """;

    public TrackingService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static ResponseStatusException notFound(String name) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, name + " não encontrado(a).");
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static final RowMapper<Product> PRODUCT_MAPPER = (rs, row) -> new Product(
            uuid(rs, "id"), rs.getString("sku"), rs.getString("name"), rs.getString("unit"));

    private static final RowMapper<Location> LOCATION_MAPPER = (rs, row) -> new Location(
            uuid(rs, "id"), rs.getString("code"), rs.getString("name"), rs.getString("kind"));

    private static final RowMapper<Shipment> SHIPMENT_MAPPER = (rs, row) -> new Shipment(
            uuid(rs, "id"), rs.getString("reference"), rs.getString("truck_plate"),
            rs.getString("driver_name"), rs.getString("customer_name"),
            rs.getString("customer_address"), rs.getString("status"),
            instant(rs, "created_at"), instant(rs, "departed_at"),
            instant(rs, "arrived_at"), instant(rs, "delivered_at"), rs.getString("received_by"));

    private static final RowMapper<TrackingUnit> UNIT_MAPPER = (rs, row) -> new TrackingUnit(
            uuid(rs, "id"), rs.getString("tracking_code"), uuid(rs, "product_id"),
            rs.getString("product_name"), rs.getString("product_sku"), rs.getString("lot"),
            rs.getInt("quantity"), rs.getString("status"), uuid(rs, "location_id"),
            rs.getString("location_name"), uuid(rs, "shipment_id"),
            rs.getString("shipment_reference"), instant(rs, "created_at"));

    private static final RowMapper<TrackingEvent> EVENT_MAPPER = (rs, row) -> new TrackingEvent(
            uuid(rs, "id"), uuid(rs, "unit_id"), rs.getString("type"),
            uuid(rs, "from_location_id"), uuid(rs, "to_location_id"), uuid(rs, "shipment_id"),
            rs.getString("actor"), rs.getString("details"), instant(rs, "occurred_at"));

    private record UnitState(UUID id, UUID locationId, UUID shipmentId, String status) { }

    private static final RowMapper<UnitState> STATE_MAPPER = (rs, row) -> new UnitState(
            uuid(rs, "id"), uuid(rs, "location_id"), uuid(rs, "shipment_id"), rs.getString("status"));

    private <T> T one(String sql, RowMapper<T> mapper, String resource, Object... params) {
        List<T> results = jdbc.query(sql, mapper, params);
        if (results.isEmpty()) throw notFound(resource);
        return results.getFirst();
    }

    private Shipment lockShipment(UUID id) {
        return one("SELECT * FROM shipment WHERE id = ? FOR UPDATE", SHIPMENT_MAPPER,
                "Remessa", id);
    }

    private UnitState lockUnit(UUID id) {
        return one("SELECT id, location_id, shipment_id, status FROM tracking_unit WHERE id = ? FOR UPDATE",
                STATE_MAPPER, "Volume", id);
    }

    private Location location(UUID id) {
        return one("SELECT * FROM location WHERE id = ?", LOCATION_MAPPER, "Local", id);
    }

    private void event(UUID unitId, String type, UUID from, UUID to, UUID shipmentId,
                       String actor, String details, Instant when) {
        jdbc.update("""
                INSERT INTO tracking_event (id, unit_id, type, from_location_id, to_location_id,
                                            shipment_id, actor, details, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), unitId, type, from, to, shipmentId,
                actor, details, Timestamp.from(when));
    }

    @Transactional(readOnly = true)
    public List<Product> products() {
        return jdbc.query("SELECT * FROM product ORDER BY name", PRODUCT_MAPPER);
    }

    @Transactional
    public Product addProduct(ProductRequest request) {
        Product product = new Product(UUID.randomUUID(), clean(request.sku()).toUpperCase(),
                clean(request.name()), clean(request.unit()));
        jdbc.update("INSERT INTO product (id, sku, name, unit) VALUES (?, ?, ?, ?)",
                product.id(), product.sku(), product.name(), product.unit());
        return product;
    }

    @Transactional(readOnly = true)
    public List<Location> locations() {
        return jdbc.query("SELECT * FROM location ORDER BY code", LOCATION_MAPPER);
    }

    @Transactional
    public Location addLocation(LocationRequest request) {
        String kind = clean(request.kind()).toUpperCase();
        if (!Set.of("PRODUCAO", "ESTOQUE", "EXPEDICAO").contains(kind)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tipo de local deve ser PRODUCAO, ESTOQUE ou EXPEDICAO.");
        }
        Location loc = new Location(UUID.randomUUID(), clean(request.code()).toUpperCase(),
                clean(request.name()), kind);
        jdbc.update("INSERT INTO location (id, code, name, kind) VALUES (?, ?, ?, ?)",
                loc.id(), loc.code(), loc.name(), loc.kind());
        return loc;
    }

    @Transactional(readOnly = true)
    public List<TrackingUnit> units() {
        return jdbc.query(UNIT_SELECT + " ORDER BY u.created_at DESC LIMIT 200", UNIT_MAPPER);
    }

    @Transactional(readOnly = true)
    public TrackingUnit unitById(UUID id) {
        return one(UNIT_SELECT + " WHERE u.id = ?", UNIT_MAPPER, "Volume", id);
    }

    @Transactional(readOnly = true)
    public TrackingUnit unitByCode(String code) {
        return one(UNIT_SELECT + " WHERE u.tracking_code = ?", UNIT_MAPPER, "Código", clean(code));
    }

    @Transactional(readOnly = true)
    public List<TrackingEvent> events(UUID unitId) {
        return jdbc.query("SELECT * FROM tracking_event WHERE unit_id = ? ORDER BY occurred_at, id",
                EVENT_MAPPER, unitId);
    }

    @Transactional(readOnly = true)
    public UnitDetail detail(String code) {
        TrackingUnit unit = unitByCode(code);
        return new UnitDetail(unit, events(unit.id()));
    }

    @Transactional
    public UnitDetail produce(ProduceRequest request, String actor) {
        one("SELECT * FROM product WHERE id = ?", PRODUCT_MAPPER, "Produto", request.productId());
        Location origin = location(request.locationId());
        if (!origin.kind().equals("PRODUCAO")) {
            throw conflict("O registro de produção exige um local do tipo PRODUCAO.");
        }
        UUID id = UUID.randomUUID();
        String trackingCode = "RT-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        Instant when = Instant.now();
        jdbc.update("""
                INSERT INTO tracking_unit (id, tracking_code, product_id, lot, quantity, status,
                                           location_id, shipment_id, created_at)
                VALUES (?, ?, ?, ?, ?, 'PRODUZIDO', ?, NULL, ?)
                """, id, trackingCode, request.productId(), clean(request.lot()), request.quantity(),
                origin.id(), Timestamp.from(when));
        event(id, "PRODUCAO", null, origin.id(), null, actor, "Produção registrada", when);
        return detail(trackingCode);
    }

    @Transactional
    public UnitDetail move(UUID id, MoveRequest request, String actor) {
        UnitState unit = lockUnit(id);
        if (!Set.of("PRODUZIDO", "ARMAZENADO").contains(unit.status())) {
            throw conflict("Somente volumes não carregados podem ser movimentados internamente.");
        }
        Location dest = location(request.destinationLocationId());
        if (unit.locationId().equals(dest.id())) {
            throw conflict("Destino deve ser diferente da localização atual.");
        }
        if (dest.kind().equals("PRODUCAO")) {
            throw conflict("A movimentação deve ter como destino ESTOQUE ou EXPEDICAO.");
        }
        Instant when = Instant.now();
        jdbc.update("UPDATE tracking_unit SET location_id = ?, status = 'ARMAZENADO' WHERE id = ?",
                dest.id(), id);
        event(id, "MOVIMENTACAO", unit.locationId(), dest.id(), null,
                actor, clean(request.details()), when);
        return new UnitDetail(unitById(id), events(id));
    }

    @Transactional(readOnly = true)
    public List<Shipment> shipments() {
        return jdbc.query("SELECT * FROM shipment ORDER BY created_at DESC LIMIT 200", SHIPMENT_MAPPER);
    }

    @Transactional
    public Shipment addShipment(ShipmentRequest request) {
        UUID id = UUID.randomUUID();
        String reference = "REM-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toUpperCase();
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO shipment (id, reference, truck_plate, driver_name, customer_name,
                                      customer_address, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, 'PREPARACAO', ?)
                """, id, reference, clean(request.truckPlate()).toUpperCase(),
                clean(request.driverName()), clean(request.customerName()),
                clean(request.customerAddress()), Timestamp.from(now));
        return shipment(id);
    }

    @Transactional(readOnly = true)
    public Shipment shipment(UUID id) {
        return one("SELECT * FROM shipment WHERE id = ?", SHIPMENT_MAPPER, "Remessa", id);
    }

    @Transactional(readOnly = true)
    public ShipmentDetail shipmentDetail(UUID id) {
        return new ShipmentDetail(shipment(id), jdbc.query(
                UNIT_SELECT + " WHERE u.shipment_id = ? ORDER BY u.created_at", UNIT_MAPPER, id));
    }

    @Transactional
    public ShipmentDetail load(UUID shipmentId, UUID unitId, String actor) {
        Shipment shipment = lockShipment(shipmentId);
        if (!shipment.status().equals("PREPARACAO")) {
            throw conflict("O carregamento só é permitido antes da saída do caminhão.");
        }
        UnitState unit = lockUnit(unitId);
        if (!Set.of("PRODUZIDO", "ARMAZENADO").contains(unit.status()) || unit.shipmentId() != null) {
            throw conflict("Volume já carregado ou com status incompatível.");
        }
        if (!location(unit.locationId()).kind().equals("EXPEDICAO")) {
            throw conflict("Movimente o volume para uma doca de EXPEDICAO antes de carregar.");
        }
        Instant when = Instant.now();
        jdbc.update("""
                UPDATE tracking_unit SET location_id = NULL, shipment_id = ?, status = 'CARREGADO'
                WHERE id = ?
                """, shipmentId, unitId);
        event(unitId, "CARREGAMENTO", unit.locationId(), null, shipmentId, actor,
                "Entrada no caminhão " + shipment.truckPlate(), when);
        return shipmentDetail(shipmentId);
    }

    private List<UUID> shipmentUnitIds(UUID shipmentId) {
        return jdbc.query("SELECT id FROM tracking_unit WHERE shipment_id = ? ORDER BY tracking_code",
                (rs, row) -> uuid(rs, "id"), shipmentId);
    }

    private void requireStatus(Shipment shipment, String expected, String action) {
        if (!shipment.status().equals(expected)) {
            throw conflict("Não é possível " + action + ": situação atual da remessa é " + shipment.status());
        }
    }

    @Transactional
    public ShipmentDetail depart(UUID id, String actor) {
        Shipment shipment = lockShipment(id);
        requireStatus(shipment, "PREPARACAO", "despachar");
        List<UUID> items = shipmentUnitIds(id);
        if (items.isEmpty()) throw conflict("É necessário carregar pelo menos um volume.");
        Instant when = Instant.now();
        jdbc.update("UPDATE shipment SET status = 'EM_TRANSITO', departed_at = ? WHERE id = ?",
                Timestamp.from(when), id);
        for (UUID unitId : items) {
            jdbc.update("UPDATE tracking_unit SET status = 'EM_TRANSITO' WHERE id = ?", unitId);
            event(unitId, "SAIDA", null, null, id, actor, "Veículo despachado", when);
        }
        return shipmentDetail(id);
    }

    @Transactional
    public ShipmentDetail arrive(UUID id, String actor) {
        Shipment shipment = lockShipment(id);
        requireStatus(shipment, "EM_TRANSITO", "registrar chegada");
        Instant when = Instant.now();
        jdbc.update("UPDATE shipment SET status = 'NO_CLIENTE', arrived_at = ? WHERE id = ?",
                Timestamp.from(when), id);
        for (UUID unitId : shipmentUnitIds(id)) {
            jdbc.update("UPDATE tracking_unit SET status = 'NO_CLIENTE' WHERE id = ?", unitId);
            event(unitId, "CHEGADA", null, null, id, actor, "Chegada registrada no cliente", when);
        }
        return shipmentDetail(id);
    }

    @Transactional
    public ShipmentDetail deliver(UUID id, DeliverRequest request, String actor) {
        Shipment shipment = lockShipment(id);
        requireStatus(shipment, "NO_CLIENTE", "confirmar entrega");
        Instant when = Instant.now();
        String recipient = clean(request.receivedBy());
        jdbc.update("""
                UPDATE shipment SET status = 'ENTREGUE', delivered_at = ?, received_by = ?
                WHERE id = ?
                """, Timestamp.from(when), recipient, id);
        for (UUID unitId : shipmentUnitIds(id)) {
            jdbc.update("UPDATE tracking_unit SET status = 'ENTREGUE' WHERE id = ?", unitId);
            event(unitId, "ENTREGA", null, null, id, actor,
                    "Recebido por: " + recipient, when);
        }
        return shipmentDetail(id);
    }

    @Transactional(readOnly = true)
    public PublicTracking publicTracking(String code) {
        TrackingUnit unit = unitByCode(code);
        List<PublicEvent> exposed = events(unit.id()).stream()
                .filter(e -> Set.of("PRODUCAO", "SAIDA", "CHEGADA", "ENTREGA").contains(e.type()))
                .map(e -> new PublicEvent(e.type(), e.occurredAt(), switch (e.type()) {
                    case "PRODUCAO" -> "Produção registrada";
                    case "SAIDA" -> "Saiu para entrega";
                    case "CHEGADA" -> "Chegou ao destino";
                    default -> "Entrega confirmada";
                }))
                .toList();
        return new PublicTracking(unit.trackingCode(), unit.productName(), unit.lot(),
                unit.quantity(), unit.status(), exposed);
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard() {
        return new Dashboard(
                count("SELECT COUNT(*) FROM product"),
                count("SELECT COUNT(*) FROM tracking_unit"),
                count("SELECT COUNT(*) FROM shipment WHERE status <> 'ENTREGUE'"),
                count("SELECT COUNT(*) FROM shipment WHERE status = 'ENTREGUE'"),
                count("SELECT COUNT(*) FROM tracking_unit WHERE status = 'EM_TRANSITO'"),
                count("SELECT COUNT(*) FROM tracking_unit WHERE status = 'ENTREGUE'"));
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }
}
