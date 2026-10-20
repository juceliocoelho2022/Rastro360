package br.com.rastro360;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class PublicTrackingProxyTest {
    private static final String CODE = "RT-1234567890ABCDEF1234567890ABCDEF";
    private HttpServer backend;
    private PublicTrackingProxy gateway;
    private final AtomicInteger backendRequests = new AtomicInteger();
    private final AtomicReference<String> receivedAuth = new AtomicReference<>();
    private final AtomicReference<String> receivedCookie = new AtomicReference<>();
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void start() throws Exception {
        backend = HttpServer.create(
                new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        backend.createContext("/", exchange -> {
            backendRequests.incrementAndGet();
            receivedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            receivedCookie.set(exchange.getRequestHeaders().getFirst("Cookie"));
            String route = exchange.getRequestURI().getRawPath();
            String body = "/tracking.html".equals(route)
                    ? "<!doctype html><title>Rastro360</title>"
                    : "{\"trackingCode\":\"" + CODE + "\",\"status\":\"PRODUZIDO\"}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type",
                    "/tracking.html".equals(route) ? "text/html" : "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var response = exchange.getResponseBody()) { response.write(bytes); }
        });
        backend.start();
        gateway = new PublicTrackingProxy(0,
                URI.create("http://127.0.0.1:" + backend.getAddress().getPort()));
        gateway.start();
    }

    @AfterEach
    void stop() {
        if (gateway != null) gateway.close();
        if (backend != null) backend.stop(0);
    }

    private HttpResponse<String> request(String path, String method) throws Exception {
        var builder = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + gateway.port() + path))
                .header("Authorization", "Basic should-not-forward")
                .header("Cookie", "session=should-not-forward")
                .timeout(java.time.Duration.ofSeconds(5));
        if ("GET".equals(method)) builder.GET();
        else builder.method(method, HttpRequest.BodyPublishers.noBody());
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void onlyPublicTrackingHtmlAndValidApiAreForwarded() throws Exception {
        var page = request("/tracking.html?code=" + CODE, "GET");
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains("Rastro360");
        assertThat(page.headers().firstValue("Cache-Control")).hasValueSatisfying(
                value -> assertThat(value).contains("no-store"));
        assertThat(page.headers().firstValue("X-Frame-Options")).contains("DENY");

        var api = request("/api/public/track/" + CODE, "GET");
        assertThat(api.statusCode()).isEqualTo(200);
        assertThat(api.body()).contains(CODE);
        assertThat(api.headers().firstValue("Content-Type")).hasValueSatisfying(
                value -> assertThat(value).contains("application/json"));

        assertThat(backendRequests.get()).isEqualTo(2);
        assertThat(receivedAuth.get()).isNull();
        assertThat(receivedCookie.get()).isNull();
    }

    @Test
    void administratorAndOtherEndpointsNeverReachBackend() throws Exception {
        String[] denied = {
                "/", "/index.html", "/scanner.html", "/label.html", "/assets/app.js",
                "/api/products", "/api/products/" + java.util.UUID.randomUUID() + "/image",
                "/api/units", "/api/shipments", "/actuator/health",
                "/api/public/track/nope",
                "/tracking.html?code=invalid",
                "/tracking.html?code=" + CODE + "&admin=1"
        };
        for (String path : denied) {
            var response = request(path, "GET");
            assertThat(response.statusCode()).as(path).isBetween(400, 499);
        }
        assertThat(backendRequests.get()).isZero();
    }

    @Test
    void onlyGetIsAllowed() throws Exception {
        assertThat(request("/tracking.html", "POST").statusCode()).isEqualTo(405);
        assertThat(request("/api/public/track/" + CODE, "DELETE").statusCode())
                .isEqualTo(405);
        assertThat(backendRequests.get()).isZero();
    }

    @Test
    void publicGatewayIsBoundOnlyToLoopback() {
        assertThat(gateway.port()).isPositive();
        assertThat(backend.getAddress().getAddress().isLoopbackAddress()).isTrue();
    }
}
