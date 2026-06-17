package dev.localassistant.assistant.rag.support;

import dev.localassistant.assistant.adapters.outbound.cdq.ProductPageTextExtractor;
import dev.localassistant.assistant.rag.ProductKnowledgePort;
import dev.localassistant.assistant.rag.ProductPageResult;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class FixtureProductKnowledgePort implements ProductKnowledgePort {

    private final String extractedText;

    private FixtureProductKnowledgePort(String extractedText) {
        this.extractedText = Objects.requireNonNull(extractedText, "extractedText");
    }

    public static FixtureProductKnowledgePort fromClasspathHtml(String classpathResource) throws IOException {
        String html =
                new ClassPathResource(classpathResource).getContentAsString(StandardCharsets.UTF_8);
        ProductPageTextExtractor extractor = new ProductPageTextExtractor();
        return new FixtureProductKnowledgePort(extractor.extractPlainText(html));
    }

    public FixtureProductKnowledgePort withExtractedText(String extractedText) {
        return new FixtureProductKnowledgePort(extractedText);
    }

    @Override
    public ProductPageResult fetchAndExtract(String sourceUrl) {
        return new ProductPageResult.Success(extractedText);
    }
}
