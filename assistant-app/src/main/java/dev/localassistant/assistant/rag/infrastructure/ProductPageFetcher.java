package dev.localassistant.assistant.rag.infrastructure;

import dev.localassistant.assistant.config.AssistantRagRetrievalProperties;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
@Profile("!test")
final class ProductPageFetcher {

    private final HttpClient httpClient;
    private final Duration requestTimeout;

    ProductPageFetcher(AssistantRagRetrievalProperties retrievalProperties) {
        this.httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        this.requestTimeout = Duration.ofSeconds(retrievalProperties.fetchTimeoutSeconds());
    }

    String fetchHtml(String sourceUrl) throws IOException, InterruptedException {
        if (StringUtils.isBlank(sourceUrl)) {
            throw new IllegalArgumentException("sourceUrl must not be blank");
        }

        final var request =
                HttpRequest.newBuilder()
                        .uri(URI.create(sourceUrl))
                        .timeout(requestTimeout)
                        .header("Accept", "text/html")
                        .GET()
                        .build();

        final var response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ProductPageFetchException(
                    "Product page request failed with HTTP status " + response.statusCode());
        }

        final var body = response.body();
        if (StringUtils.isBlank(body)) {
            throw new ProductPageFetchException("Product page response body was empty");
        }
        return body;
    }
}
