package dev.localassistant.assistant.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static dev.localassistant.assistant.config.PropertiesBinding.bind;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssistantRagRetrievalPropertiesTest {

    @Test
    void appliesDefaultsWhenUnset() {
        AssistantRagRetrievalProperties properties = bind(
                "assistant.rag", AssistantRagRetrievalProperties.class, Map.of());

        assertThat(properties.topK()).isEqualTo(5);
        assertThat(properties.relevanceThreshold()).isEqualTo(0.5);
        assertThat(properties.sourceUrl()).isEqualTo("https://www.cdq.com/products/cdq-fraud-guard");
        assertThat(properties.fetchTimeoutSeconds()).isEqualTo(30);
    }

    @Test
    void bindsPopulatedRoundTrip() {
        AssistantRagRetrievalProperties properties = bind(
                "assistant.rag",
                AssistantRagRetrievalProperties.class,
                Map.of(
                        "assistant.rag.top-k", "8",
                        "assistant.rag.relevance-threshold", "0.7",
                        "assistant.rag.source-url", "https://example.test/page",
                        "assistant.rag.fetch-timeout-seconds", "15"));

        assertThat(properties.topK()).isEqualTo(8);
        assertThat(properties.relevanceThreshold()).isEqualTo(0.7);
        assertThat(properties.sourceUrl()).isEqualTo("https://example.test/page");
        assertThat(properties.fetchTimeoutSeconds()).isEqualTo(15);
    }

    @Test
    void rejectsRelevanceThresholdAboveOne() {
        assertThatThrownBy(() -> bind(
                "assistant.rag",
                AssistantRagRetrievalProperties.class,
                Map.of("assistant.rag.relevance-threshold", "1.5")))
                .hasMessageContaining("assistant.rag")
                .hasStackTraceContaining("relevanceThreshold");
    }

    @Test
    void rejectsNonPositiveTopK() {
        assertThatThrownBy(() -> bind(
                "assistant.rag",
                AssistantRagRetrievalProperties.class,
                Map.of("assistant.rag.top-k", "0")))
                .hasMessageContaining("assistant.rag")
                .hasStackTraceContaining("topK");
    }
}
