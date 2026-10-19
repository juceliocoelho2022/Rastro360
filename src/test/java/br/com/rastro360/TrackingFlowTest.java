package br.com.rastro360;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TrackingFlowTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    private JsonNode authenticatedPost(String path, String body) throws Exception {
        String response = mvc.perform(post(path)
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("operador", "dev12345"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body == null ? "" : body))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(response);
    }

    private JsonNode authenticatedGet(String path) throws Exception {
        return mapper.readTree(mvc.perform(get(path)
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("operador", "dev12345")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    void fullJourneyFromProductionToCustomerDelivery() throws Exception {
        JsonNode locations = authenticatedGet("/api/locations");
        String production = null;
        String dock = null;
        for (JsonNode l : locations) {
            if (l.get("kind").asText().equals("PRODUCAO")) production = l.get("id").asText();
            if (l.get("kind").asText().equals("EXPEDICAO")) dock = l.get("id").asText();
        }
        assertThat(production).isNotNull();
        assertThat(dock).isNotNull();

        String sku = "P-" + UUID.randomUUID().toString().substring(0, 8);
        JsonNode product = authenticatedPost("/api/products", """
                {"sku":"%s","name":"Caixa de demonstração","unit":"un"}
                """.formatted(sku));
        JsonNode produced = authenticatedPost("/api/units", """
                {"productId":"%s","lot":"FAB-2026-001","quantity":25,"locationId":"%s"}
                """.formatted(product.get("id").asText(), production));
        JsonNode unit = produced.get("unit");
        String unitId = unit.get("id").asText();
        String code = unit.get("trackingCode").asText();
        assertThat(unit.get("status").asText()).isEqualTo("PRODUZIDO");

        JsonNode moved = authenticatedPost("/api/units/" + unitId + "/move", """
                {"destinationLocationId":"%s","details":"A caminho da doca"}
                """.formatted(dock));
        assertThat(moved.get("unit").get("status").asText()).isEqualTo("ARMAZENADO");

        JsonNode shipment = authenticatedPost("/api/shipments", """
                {"truckPlate":"ABC1D23","driverName":"Motorista","customerName":"Cliente Final",
                 "customerAddress":"Rua Exemplo, 1 - São Paulo/SP"}
                """);
        String shipmentId = shipment.get("id").asText();

        JsonNode loaded = authenticatedPost("/api/shipments/" + shipmentId + "/load/" + unitId, null);
        assertThat(loaded.get("units").get(0).get("status").asText()).isEqualTo("CARREGADO");
        mvc.perform(post("/api/shipments/" + shipmentId + "/load/" + unitId)
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("operador", "dev12345")))
                .andExpect(status().isConflict());

        JsonNode departed = authenticatedPost("/api/shipments/" + shipmentId + "/depart", null);
        assertThat(departed.get("shipment").get("status").asText()).isEqualTo("EM_TRANSITO");
        JsonNode arrived = authenticatedPost("/api/shipments/" + shipmentId + "/arrive", null);
        assertThat(arrived.get("shipment").get("status").asText()).isEqualTo("NO_CLIENTE");
        JsonNode delivered = authenticatedPost("/api/shipments/" + shipmentId + "/deliver",
                "{\"receivedBy\":\"Maria Silva\"}");
        assertThat(delivered.get("shipment").get("status").asText()).isEqualTo("ENTREGUE");
        assertThat(delivered.get("shipment").get("receivedBy").asText()).isEqualTo("Maria Silva");

        JsonNode trace = authenticatedGet("/api/units/" + code);
        assertThat(trace.get("unit").get("status").asText()).isEqualTo("ENTREGUE");
        assertThat(trace.get("events").size()).isEqualTo(6);
        JsonNode publicTrace = mapper.readTree(mvc.perform(get("/api/public/track/" + code))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(publicTrace.get("events").size()).isEqualTo(4);
        assertThat(publicTrace.toString()).doesNotContain("truckPlate", "driverName", "customerAddress", "receivedBy", "actor");
    }

    @Test
    void privateEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
        mvc.perform(get("/tracking.html")).andExpect(status().isOk());
    }
}
