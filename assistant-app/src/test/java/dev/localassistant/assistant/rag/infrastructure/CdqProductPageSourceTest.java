package dev.localassistant.assistant.rag.infrastructure;

import dev.localassistant.assistant.rag.domain.ProductPageResult;
import dev.localassistant.assistant.rag.domain.port.outbound.ProductPageSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import dev.localassistant.assistant.config.AssistantRagRetrievalProperties;

import com.sun.net.httpserver.HttpServer;

import static org.assertj.core.api.Assertions.assertThat;

class CdqProductPageSourceTest {

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
                new ProductPageFetcher(new AssistantRagRetrievalProperties(3, 0.5, sourceUrl, 2));
        CdqProductPageSource productPageSource =
                new CdqProductPageSource(fetcher, new ProductPageTextExtractor());

        ProductPageResult result = productPageSource.fetchAndExtract(new ProductPageSource.Command(sourceUrl));

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
                new ProductPageFetcher(new AssistantRagRetrievalProperties(3, 0.5, sourceUrl, 2));
        CdqProductPageSource productPageSource =
                new CdqProductPageSource(fetcher, new ProductPageTextExtractor());

        ProductPageResult result = productPageSource.fetchAndExtract(new ProductPageSource.Command(emptyBodyUrl));

        assertThat(result).isInstanceOf(ProductPageResult.SourceUnavailable.class);
    }
}
