package br.com.rastro360;

import br.com.rastro360.ApiModels.Product;
import br.com.rastro360.ApiModels.ProductRequest;
import br.com.rastro360.ApiModels.ProduceRequest;
import br.com.rastro360.ApiModels.Location;
import br.com.rastro360.ApiModels.UnitDetail;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicMobileTrackingTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired TrackingService trackingService;

    @Test
    void publicMobilePageRemainsAccessibleWithoutLoginAndUpdatesEvery30Seconds() throws Exception {
        String html = mvc.perform(get("/tracking.html"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("name=\"viewport\"");
        assertThat(html).contains("name=\"robots\" content=\"noindex,nofollow,noarchive\"");
        assertThat(html).contains("name=\"referrer\" content=\"no-referrer\"");
        assertThat(html).contains("setInterval(");
        assertThat(html).contains("30000");
        assertThat(html).contains("visibilitychange");
        assertThat(html).contains("navigator.onLine");
        assertThat(html).contains("credentials: 'omit'");
        assertThat(html).contains("cache: 'no-store'");
        assertThat(html).contains("document.createElement('div')");
        assertThat(html).contains("statusLabels");
        assertThat(html).contains("Compartilhar");
        assertThat(html).contains("localhost");
    }

    @Test
    void publicEndpointRefreshIsNotCachedAndContainsNoPrivateShipmentData() throws Exception {
        Product product = trackingService.addProduct(new ProductRequest(
                "MOBILE-" + UUID.randomUUID().toString().substring(0, 8),
                "Caixa de teste", "un"));
        Location location = trackingService.locations().stream()
                .filter(l -> l.kind().equals("PRODUCAO")).findFirst().orElseThrow();
        UnitDetail unit = trackingService.produce(new ProduceRequest(
                product.id(), "LOTE-TESTE", 1, location.id()), "operador");

        String response = mvc.perform(get("/api/public/track/" + unit.unit().trackingCode()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Robots-Tag", "noindex, nofollow"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(jsonPath("$.status").value("PRODUZIDO"))
                .andExpect(jsonPath("$.events.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        JsonNode payload = mapper.readTree(response);
        assertThat(payload.toString()).doesNotContain(
                "driverName", "truckPlate", "customerAddress", "customerName",
                "receivedBy", "actor", "fromLocationId", "toLocationId", "productId");
        assertThat(payload.get("events").get(0).get("description").asText())
                .isEqualTo("Produção registrada");
    }

    @Test
    void invalidCodeIsRejectedWithoutDatabaseLookup() throws Exception {
        mvc.perform(get("/api/public/track/not-a-tracking-code"))
                .andExpect(status().isNotFound());
    }

    @Test
    void canonicalUrlCanBeConfiguredForCustomerQrCodes() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8081);
        var generator = new QrCodeService("https://rastreio.exemplo.com.br/");
        String code = "RT-1234567890ABCDEF1234567890ABCDEF";
        assertThat(generator.trackingUrl(code, request))
                .isEqualTo("https://rastreio.exemplo.com.br/tracking.html?code=" + code);
    }
}
