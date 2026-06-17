package dev.localassistant.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

final class AssistantApiClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final URI baseUri;
    private final HttpClient httpClient;

    AssistantApiClient(String baseUrl) {
        this.baseUri = URI.create(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
        this.httpClient =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    static Optional<AssistantApiClient> tryConnect() {
        String configured = System.getProperty("assistant.e2e.base-url");
        String baseUrl =
                configured == null || configured.isBlank()
                        ? "http://localhost:8080"
                        : configured;
        AssistantApiClient client = new AssistantApiClient(baseUrl);
        if (!client.isReachable()) {
            return Optional.empty();
        }
        return Optional.of(client);
    }

    boolean isReachable() {
        try {
            HttpRequest request =
                    HttpRequest.newBuilder(baseUri.resolve("/api/chat"))
                            .timeout(Duration.ofSeconds(10))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{\"question\":\"e2e reachability\"}"))
                            .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException exception) {
            return false;
        }
    }

    JsonNode chat(String question) throws IOException, InterruptedException {
        String body = OBJECT_MAPPER.writeValueAsString(new ChatRequest(question));
        HttpRequest request =
                HttpRequest.newBuilder(baseUri.resolve("/api/chat"))
                        .timeout(Duration.ofSeconds(120))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    "POST /api/chat returned HTTP "
                            + response.statusCode()
                            + ": "
                            + response.body());
        }
        return OBJECT_MAPPER.readTree(response.body());
    }

    int chatExpectingStatus(String question, int expectedStatus) throws IOException, InterruptedException {
        String body = OBJECT_MAPPER.writeValueAsString(new ChatRequest(question));
        HttpRequest request =
                HttpRequest.newBuilder(baseUri.resolve("/api/chat"))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode();
    }

    private record ChatRequest(String question) {}
}
