package dev.localassistant.assistant.adapters.outbound.cdq;

import dev.localassistant.assistant.rag.ProductPageResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;

import com.sun.net.httpserver.HttpServer;

import static org.assertj.core.api.Assertions.assertThat;

class CdqProductKnowledgeAdapterTest {

    private HttpServer httpServer;
    private String sourceUrl;

    @BeforeEach
    void startStubServer() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext(
                "/cdq-fraud-guard",
                exchange -> {
                    exchange.sendResponseHeaders(503, -1);
                    exchange.close();
                });
        httpServer.start();
        sourceUrl = "http://localhost:" + httpServer.getAddress().getPort() + "/cdq-fraud-guard";
    }

    @AfterEach
    void stopStubServer() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    @Test
    void fetchFailureMapsToSourceUnavailableWithoutInventingProductKnowledge() {
        ProductPageFetcher fetcher =
                new ProductPageFetcher(HttpClient.newHttpClient(), Duration.ofSeconds(2));
        CdqProductKnowledgeAdapter adapter =
                new CdqProductKnowledgeAdapter(fetcher, new ProductPageTextExtractor());

        ProductPageResult result = adapter.fetchAndExtract(sourceUrl);

        assertThat(result).isInstanceOf(ProductPageResult.SourceUnavailable.class);
        ProductPageResult.SourceUnavailable unavailable = (ProductPageResult.SourceUnavailable) result;
        assertThat(unavailable.sourceLabel()).isEqualTo("CDQ product page");
        assertThat(unavailable.message()).contains("503");
        assertThat(unavailable.hint()).contains("CDQ product page URL");
    }

    @Test
    void emptyResponseBodyMapsToSourceUnavailable() throws IOException {
        httpServer.stop(0);
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext(
                "/empty",
                exchange -> {
                    exchange.sendResponseHeaders(200, 0);
                    try (OutputStream responseBody = exchange.getResponseBody()) {
                        responseBody.flush();
                    }
                });
        httpServer.start();
        String emptyBodyUrl = "http://localhost:" + httpServer.getAddress().getPort() + "/empty";

        ProductPageFetcher fetcher =
                new ProductPageFetcher(HttpClient.newHttpClient(), Duration.ofSeconds(2));
        CdqProductKnowledgeAdapter adapter =
                new CdqProductKnowledgeAdapter(fetcher, new ProductPageTextExtractor());

        ProductPageResult result = adapter.fetchAndExtract(emptyBodyUrl);

        assertThat(result).isInstanceOf(ProductPageResult.SourceUnavailable.class);
    }
}
