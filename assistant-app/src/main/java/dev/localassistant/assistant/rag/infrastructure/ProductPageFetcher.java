package dev.localassistant.assistant.rag.infrastructure;

import dev.localassistant.assistant.rag.infrastructure.config.AssistantRagRetrievalProperties;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

    ProductPageFetcher(final AssistantRagRetrievalProperties retrievalProperties) {
        httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        requestTimeout = Duration.ofSeconds(retrievalProperties.fetchTimeoutSeconds());
    }

    String fetchHtml(final String sourceUrl) throws IOException, InterruptedException {
        if (StringUtils.isBlank(sourceUrl)) {
            throw new IllegalArgumentException("sourceUrl must not be blank");
        }

        final var request =
            HttpRequest.newBuilder()
                .uri(URI.create(sourceUrl))
                .timeout(requestTimeout)
                .header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE)
                .GET()
                .build();

        final var response =
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (HttpStatus.Series.SUCCESSFUL != HttpStatus.Series.resolve(response.statusCode())) {
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
