package dev.localassistant.assistant.adapters.outbound.cdq;

import dev.localassistant.assistant.rag.ProductKnowledgePort;
import dev.localassistant.assistant.rag.ProductPageResult;

import java.io.IOException;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

public final class CdqProductKnowledgeAdapter implements ProductKnowledgePort {

    private static final String SOURCE_LABEL = "CDQ product page";

    private final ProductPageFetcher productPageFetcher;
    private final ProductPageTextExtractor productPageTextExtractor;

    public CdqProductKnowledgeAdapter(
            ProductPageFetcher productPageFetcher, ProductPageTextExtractor productPageTextExtractor) {
        this.productPageFetcher = Objects.requireNonNull(productPageFetcher, "productPageFetcher");
        this.productPageTextExtractor =
                Objects.requireNonNull(productPageTextExtractor, "productPageTextExtractor");
    }

    @Override
    public ProductPageResult fetchAndExtract(String sourceUrl) {
        if (StringUtils.isBlank(sourceUrl)) {
            throw new IllegalArgumentException("sourceUrl must not be blank");
        }

        try {
            String html = productPageFetcher.fetchHtml(sourceUrl);
            String extractedText = productPageTextExtractor.extractPlainText(html);
            return new ProductPageResult.Success(extractedText);
        } catch (ProductPageFetchException | ProductPageExtractionException exception) {
            return new ProductPageResult.SourceUnavailable(
                    SOURCE_LABEL, exception.getMessage(), "Verify the configured CDQ product page URL is reachable");
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new ProductPageResult.SourceUnavailable(
                    SOURCE_LABEL,
                    "Failed to fetch CDQ product page: " + exception.getMessage(),
                    "Verify network connectivity and the configured source URL");
        }
    }
}
