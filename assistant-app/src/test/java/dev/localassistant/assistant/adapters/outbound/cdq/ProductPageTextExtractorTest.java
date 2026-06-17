package dev.localassistant.assistant.adapters.outbound.cdq;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductPageTextExtractorTest {

    private ProductPageTextExtractor extractor;
    private String fixtureHtml;

    @BeforeEach
    void setUp() throws IOException {
        extractor = new ProductPageTextExtractor();
        fixtureHtml =
                new ClassPathResource("fixtures/rag/cdq-fraud-guard-sample.html")
                        .getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void extractsExpectedProductPhrasesFromFixture() {
        String extractedText = extractor.extractPlainText(fixtureHtml);

        assertThat(extractedText).contains("CDQ Fraud Guard");
        assertThat(extractedText).contains("payment fraud");
        assertThat(extractedText).contains("prevent chargebacks");
        assertThat(extractedText).doesNotContain("window.tracking");
        assertThat(extractedText).doesNotContain("display: none");
    }

    @Test
    void rejectsBlankHtml() {
        assertThatThrownBy(() -> extractor.extractPlainText(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
