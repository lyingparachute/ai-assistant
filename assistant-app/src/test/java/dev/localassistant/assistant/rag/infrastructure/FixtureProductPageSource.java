package dev.localassistant.assistant.rag.infrastructure;

import dev.localassistant.assistant.rag.domain.ProductPageResult;
import dev.localassistant.assistant.rag.domain.port.outbound.ProductPageSource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class FixtureProductPageSource implements ProductPageSource {

    private final String extractedText;

    private FixtureProductPageSource(String extractedText) {
        this.extractedText = Objects.requireNonNull(extractedText, "extractedText");
    }

    public static FixtureProductPageSource fromClasspathHtml(String classpathResource) throws IOException {
        String html =
                new ClassPathResource(classpathResource).getContentAsString(StandardCharsets.UTF_8);
        ProductPageTextExtractor extractor = new ProductPageTextExtractor();
        return new FixtureProductPageSource(extractor.extractPlainText(html));
    }

    public FixtureProductPageSource withExtractedText(String extractedText) {
        return new FixtureProductPageSource(extractedText);
    }

    @Override
    public ProductPageResult fetchAndExtract(Command command) {
        return fetchAndExtract(command.sourceUrl());
    }

    private ProductPageResult fetchAndExtract(String sourceUrl) {
        return new ProductPageResult.Success(extractedText);
    }
}
