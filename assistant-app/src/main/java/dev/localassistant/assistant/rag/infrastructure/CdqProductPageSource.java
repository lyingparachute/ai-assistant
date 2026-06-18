package dev.localassistant.assistant.rag.infrastructure;

import dev.localassistant.assistant.rag.domain.ProductPageResult;
import dev.localassistant.assistant.rag.domain.port.outbound.ProductPageSource;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Profile("!test")
@RequiredArgsConstructor
final class CdqProductPageSource implements ProductPageSource {

    private static final String SOURCE_LABEL = "CDQ product page";

    private final ProductPageFetcher productPageFetcher;
    private final ProductPageTextExtractor productPageTextExtractor;

    @Override
    public ProductPageResult fetchAndExtract(final Command command) {
        final var sourceUrl = command.sourceUrl();
        try {
            final var html = productPageFetcher.fetchHtml(sourceUrl);
            final var extractedText = productPageTextExtractor.extractPlainText(html);
            return new ProductPageResult.Success(extractedText);
        } catch (ProductPageFetchException | ProductPageExtractionException exception) {
            return new ProductPageResult.SourceUnavailable(
                SOURCE_LABEL, exception.getMessage(), "Verify the configured CDQ product page URL is reachable");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new ProductPageResult.SourceUnavailable(
                SOURCE_LABEL, exception.getMessage(), "Verify network connectivity and the configured source URL");
        } catch (IOException exception) {
            return new ProductPageResult.SourceUnavailable(
                SOURCE_LABEL,
                "Failed to fetch CDQ product page: " + exception.getMessage(),
                "Verify network connectivity and the configured source URL");
        }
    }
}
