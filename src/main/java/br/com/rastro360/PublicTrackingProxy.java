package br.com.rastro360;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Tiny, intentionally read-only PUBLIC gateway for temporary HTTPS demos.
 *
 * cloudflared MUST point to this gateway (port 8090), never to the admin app
 * (port 8081). This gateway forwards exactly two read-only URL patterns:
 *   GET /tracking.html[?code=RT-...]
 *   GET /api/public/track/RT-...
 * It never forwards credentials, cookies or any arbitrary request headers.
 *
 * This is NOT a replacement for a hardened production gateway and auth stack.
 */
public final class PublicTrackingProxy implements AutoCloseable {
    private static final Pattern TRACKING_CODE = Pattern.compile("(?i)RT-[0-9A-F]{32}");
    private static final String API_PREFIX = "/api/public/track/";
    private static final int MAX_BYTES = 1024 * 1024;

    private final HttpServer server;
    private final HttpClient client;
    private final URI backend;
    private final ExecutorService executor;

    public PublicTrackingProxy(int port, URI backend) throws IOException {
        if (backend == null || !"http".equalsIgnoreCase(backend.getScheme())
                || !"127.0.0.1".equals(backend.getHost())
                || backend.getPort() <= 0 || backend.getRawPath().length() > 0
                || backend.getRawQuery() != null || backend.getRawFragment() != null) {
            throw new IllegalArgumentException("O backend deve ser http://127.0.0.1:<porta>.");
        }
        this.backend = backend;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.server = HttpServer.create(
                new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 32);
        this.executor = Executors.newFixedThreadPool(8);
        this.server.setExecutor(executor);
        this.server.createContext("/", this::handle);
    }

    public void start() {
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            setSecurityHeaders(exchange);
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "GET");
                respond(exchange, 405, "Método não permitido.");
                return;
            }

            URI uri = exchange.getRequestURI();
            String path = uri.getRawPath();
            String query = uri.getRawQuery();
            boolean page = "/tracking.html".equals(path);
            boolean api = path.startsWith(API_PREFIX)
                    && TRACKING_CODE.matcher(path.substring(API_PREFIX.length())).matches();

            if ((!page && !api) || (api && query != null)) {
                respond(exchange, 404, "Rota não disponível neste portal.");
                return;
            }
            // Query is constrained to one RT-code parameter, or absent for manual entry.
            if (page && query != null && !query.matches("(?i)code=RT-[0-9a-f]{32}")) {
                respond(exchange, 400, "Código inválido.");
                return;
            }

            String targetPath = path + (query == null ? "" : "?" + query);
            HttpRequest upstream = HttpRequest.newBuilder(backend.resolve(targetPath))
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .header("Accept", api ? "application/json" : "text/html")
                    .build();

            // Auth/Cookie/Host headers from the visitor are NOT forwarded.
            HttpResponse<byte[]> response = client.send(upstream, HttpResponse.BodyHandlers.ofByteArray());
            if (response.body().length > MAX_BYTES) {
                respond(exchange, 502, "Resposta muito grande.");
                return;
            }
            int code = response.statusCode();
            if (code == 200) {
                exchange.getResponseHeaders().set("Content-Type",
                        api ? "application/json; charset=utf-8" : "text/html; charset=utf-8");
                exchange.sendResponseHeaders(200, response.body().length);
                try (var out = exchange.getResponseBody()) {
                    out.write(response.body());
                }
            } else {
                // Never forward application error details or WWW-Authenticate challenges.
                respond(exchange, code == 404 ? 404 : 502,
                        code == 404 ? "Código não encontrado." : "Consulta indisponível.");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            respond(exchange, 503, "Consulta temporariamente indisponível.");
        } catch (Exception error) {
            respond(exchange, 503, "Consulta temporariamente indisponível.");
        } finally {
            exchange.close();
        }
    }

    private static void setSecurityHeaders(HttpExchange exchange) {
        var h = exchange.getResponseHeaders();
        h.set("Cache-Control", "no-store, max-age=0");
        h.set("Pragma", "no-cache");
        h.set("X-Content-Type-Options", "nosniff");
        h.set("X-Frame-Options", "DENY");
        h.set("Referrer-Policy", "no-referrer");
        h.set("X-Robots-Tag", "noindex, nofollow, noarchive");
        h.set("Content-Security-Policy",
                "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; "
                + "connect-src 'self'; base-uri 'none'; form-action 'self'; "
                + "frame-ancestors 'none'; object-src 'none'");
    }

    private static void respond(HttpExchange exchange, int status, String message) throws IOException {
        byte[] body = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        try (var out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    public static void main(String[] args) throws Exception {
        int publicPort = Integer.parseInt(System.getenv().getOrDefault("RASTRO360_PUBLIC_PORT", "8090"));
        int privatePort = Integer.parseInt(System.getenv().getOrDefault("RASTRO360_PRIVATE_PORT", "8081"));
        if (publicPort == privatePort) throw new IllegalArgumentException("As portas pública e privada devem ser diferentes.");

        PublicTrackingProxy gateway = new PublicTrackingProxy(
                publicPort, URI.create("http://127.0.0.1:" + privatePort));
        Runtime.getRuntime().addShutdownHook(new Thread(gateway::close));
        gateway.start();
        System.out.printf("Rastro360 consulta publica restrita: http://127.0.0.1:%d/tracking.html%n",
                gateway.port());
        System.out.printf("Backend privado: http://127.0.0.1:%d (NAO usar como origem do tunel)%n", privatePort);
        System.out.println("Rotas externas autorizadas: GET /tracking.html e GET /api/public/track/RT-...");
        new java.util.concurrent.CountDownLatch(1).await();
    }
}
