package dev.localassistant.assistant.rag.infrastructure.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static dev.localassistant.assistant.support.PropertiesBinding.bind;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssistantEmbeddingPropertiesTest {

    @Test
    void appliesDefaultsWhenOnlyRequiredValuePresent() {
        AssistantEmbeddingProperties properties = bind(
                "assistant.embedding",
                AssistantEmbeddingProperties.class,
                Map.of("assistant.embedding.ollama-base-url", "http://localhost:11434"));

        assertThat(properties.ollamaBaseUrl()).isEqualTo("http://localhost:11434");
        assertThat(properties.modelName()).isEqualTo("nomic-embed-text");
        assertThat(properties.documentPrefix()).isEqualTo("search_document:");
        assertThat(properties.queryPrefix()).isEqualTo("search_query:");
    }

    @Test
    void bindsPopulatedRoundTrip() {
        AssistantEmbeddingProperties properties = bind(
                "assistant.embedding",
                AssistantEmbeddingProperties.class,
                Map.of(
                        "assistant.embedding.ollama-base-url", "http://ollama.test:11434",
                        "assistant.embedding.model-name", "custom-embed",
                        "assistant.embedding.document-prefix", "doc:",
                        "assistant.embedding.query-prefix", "query:"));

        assertThat(properties.ollamaBaseUrl()).isEqualTo("http://ollama.test:11434");
        assertThat(properties.modelName()).isEqualTo("custom-embed");
        assertThat(properties.documentPrefix()).isEqualTo("doc:");
        assertThat(properties.queryPrefix()).isEqualTo("query:");
    }

    @Test
    void rejectsBlankOllamaBaseUrl() {
        assertThatThrownBy(() -> bind(
                "assistant.embedding",
                AssistantEmbeddingProperties.class,
                Map.of("assistant.embedding.ollama-base-url", "   ")))
                .hasMessageContaining("assistant.embedding")
                .hasStackTraceContaining("ollamaBaseUrl");
    }
}
