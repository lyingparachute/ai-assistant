package dev.localassistant.countries.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class StubRestCountriesServer implements AutoCloseable {

    private final HttpServer httpServer;
    private final Map<String, StubResponse> routes = new ConcurrentHashMap<>();

    public StubRestCountriesServer() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        httpServer.createContext("/", new RoutingHandler());
        httpServer.start();
    }

    public String baseUrl() {
        return "http://localhost:" + httpServer.getAddress().getPort();
    }

    public void stubNameLookup(String name, int statusCode, String body) {
        routes.put(routePath("name", name), new StubResponse(statusCode, body));
    }

    public void stubNameLookup(String name, int statusCode, String body, long delayMillis) {
        routes.put(routePath("name", name), new StubResponse(statusCode, body, delayMillis));
    }

    public void stubCapitalLookup(String capital, int statusCode, String body) {
        routes.put(routePath("capital", capital), new StubResponse(statusCode, body));
    }

    private static String routePath(String endpoint, String value) {
        return "/" + endpoint + "/" + value;
    }

    @Override
    public void close() {
        httpServer.stop(0);
    }

    private record StubResponse(int statusCode, String body, long delayMillis) {
        StubResponse(int statusCode, String body) {
            this(statusCode, body, 0L);
        }
    }

    private final class RoutingHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String lookupPath = path.contains("?") ? path.substring(0, path.indexOf('?')) : path;
            StubResponse response = routes.get(lookupPath);
            if (response == null) {
                writeResponse(exchange, 404, "[]");
                return;
            }
            if (response.delayMillis() > 0) {
                try {
                    Thread.sleep(response.delayMillis());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
            writeResponse(exchange, response.statusCode(), response.body());
        }

        private void writeResponse(HttpExchange exchange, int statusCode, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(bytes);
            }
        }
    }

    public static String readFixture(String resourcePath) throws IOException {
        try (InputStream inputStream = StubRestCountriesServer.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("Missing fixture: " + resourcePath);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
