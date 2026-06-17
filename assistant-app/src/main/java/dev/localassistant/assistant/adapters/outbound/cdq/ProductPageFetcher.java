package dev.localassistant.assistant.adapters.outbound.cdq;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

class ProductPageFetcher {

    private final HttpClient httpClient;
    private final Duration requestTimeout;

    ProductPageFetcher(HttpClient httpClient, Duration requestTimeout) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
    }

    String fetchHtml(String sourceUrl) throws IOException, InterruptedException {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new IllegalArgumentException("sourceUrl must not be blank");
        }

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(sourceUrl))
                        .timeout(requestTimeout)
                        .header("Accept", "text/html")
                        .GET()
                        .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ProductPageFetchException(
                    "Product page request failed with HTTP status " + response.statusCode());
        }

        String body = response.body();
        if (body == null || body.isBlank()) {
            throw new ProductPageFetchException("Product page response body was empty");
        }
        return body;
    }
}
