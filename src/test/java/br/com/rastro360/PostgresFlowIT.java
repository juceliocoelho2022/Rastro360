package br.com.rastro360;

import br.com.rastro360.ApiModels.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real PostgreSQL integration suite, run explicitly in GitHub Actions with
 * mvn -Dtest=PostgresFlowIT test. NOT included in ordinary mvn clean verify.
 *
 * Never connects to the developer's persistent H2 file.
 * The database used by CI is an ephemeral PostgreSQL service.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("postgres")
@EnabledIfEnvironmentVariable(named = "RASTRO360_POSTGRES_TEST", matches = "true")
class PostgresFlowIT {
    @Autowired TrackingService trackingService;
    @Autowired ProductImageService imageService;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @Test
    void flywayMigrationsWorkOnRealPostgres() throws Exception {
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
        }

        var versions = jdbc.query(
                "SELECT version FROM flyway_schema_history WHERE success = TRUE ORDER BY installed_rank",
                (rs, rowNum) -> rs.getString(1));
        assertThat(versions).contains("1", "2");

        var columns = jdbc.query(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public'"
                        + " AND table_name = 'product_image'",
                (rs, rowNum) -> rs.getString(1));
        assertThat(columns).contains("product_id", "image_data", "updated_at");

        assertThat(trackingService.locations()).extracting(Location::code)
                .contains("PROD-01", "EST-01", "EXP-01");

        mvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
    }

    @Test
    void completeLogisticsJourneyAndProductPhotoPersistInPostgres() throws Exception {
        var locations = trackingService.locations();
        Location origin = locations.stream().filter(l -> l.kind().equals("PRODUCAO"))
                .findFirst().orElseThrow();
        Location stock = locations.stream().filter(l -> l.kind().equals("ESTOQUE"))
                .findFirst().orElseThrow();
        Location dock = locations.stream().filter(l -> l.kind().equals("EXPEDICAO"))
                .findFirst().orElseThrow();

        var product = trackingService.addProduct(new ProductRequest(
                "PG-" + UUID.randomUUID().toString().substring(0, 12),
                "Produto PostgreSQL de teste", "un"));
        UnitDetail produced = trackingService.produce(new ProduceRequest(
                product.id(), "LOTE-PG-001", 3, origin.id()), "teste-ci");
        var unit = produced.unit();
        String code = unit.trackingCode();
        assertThat(code).matches("RT-[0-9A-F]{32}");

        trackingService.move(unit.id(), new MoveRequest(stock.id(), "Entrada no estoque"), "teste-ci");
        trackingService.move(unit.id(), new MoveRequest(dock.id(), "Entrada na doca"), "teste-ci");

        var shipment = trackingService.addShipment(new ShipmentRequest(
                "ABC1D23", "Motorista ficticio", "Cliente ficticio", "Endereco ficticio"));
        trackingService.load(shipment.id(), unit.id(), "teste-ci");
        assertThatThrownBy(() -> trackingService.load(shipment.id(), unit.id(), "teste-ci"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));

        trackingService.depart(shipment.id(), "teste-ci");
        trackingService.arrive(shipment.id(), "teste-ci");
        trackingService.deliver(shipment.id(), new DeliverRequest("Recebedor ficticio"), "teste-ci");

        var complete = trackingService.detail(code);
        assertThat(complete.unit().status()).isEqualTo("ENTREGUE");
        assertThat(complete.unit().shipmentId()).isEqualTo(shipment.id());
        assertThat(complete.events()).hasSize(7);
        assertThat(trackingService.publicTracking(code).events()).hasSize(4);
        assertThat(trackingService.publicTracking(code).status()).isEqualTo("ENTREGUE");

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM tracking_event WHERE unit_id = ?", Integer.class, unit.id()))
                .isEqualTo(7);

        // Upload a generated synthetic photo. It must be stored as PostgreSQL BYTEA.
        var photo = new BufferedImage(8, 12, BufferedImage.TYPE_INT_RGB);
        var png = new ByteArrayOutputStream();
        assertThat(ImageIO.write(photo, "png", png)).isTrue();
        byte[] stored = imageService.upload(product.id(), new MockMultipartFile(
                "file", "synthetic.png", "image/png", png.toByteArray()));

        assertThat(stored).startsWith((byte) 0x89, (byte) 0x50, (byte) 0x4e, (byte) 0x47);
        assertThat(imageService.image(product.id())).isEqualTo(stored);
        byte[] databaseImage = jdbc.queryForObject(
                "SELECT image_data FROM product_image WHERE product_id = ?",
                (rs, index) -> rs.getBytes(1), product.id());
        assertThat(databaseImage).isEqualTo(stored);

        // The customer sees no internal transport details or personal data.
        mvc.perform(get("/api/public/track/" + code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENTREGUE"))
                .andExpect(jsonPath("$.events.length()").value(4));
    }
}
