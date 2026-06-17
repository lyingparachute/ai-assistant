package dev.localassistant.assistant.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static dev.localassistant.assistant.config.PropertiesBinding.bind;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssistantLlmPropertiesTest {

    @Test
    void appliesDefaultsWhenOnlyRequiredValuePresent() {
        AssistantLlmProperties properties = bind(
                "assistant.llm",
                AssistantLlmProperties.class,
                Map.of("assistant.llm.ollama-base-url", "http://localhost:11434"));

        assertThat(properties.ollamaBaseUrl()).isEqualTo("http://localhost:11434");
        assertThat(properties.modelName()).isEqualTo("qwen3:4b");
        assertThat(properties.timeoutSeconds()).isEqualTo(120);
    }

    @Test
    void bindsPopulatedRoundTrip() {
        AssistantLlmProperties properties = bind(
                "assistant.llm",
                AssistantLlmProperties.class,
                Map.of(
                        "assistant.llm.ollama-base-url", "http://ollama.test:11434",
                        "assistant.llm.model-name", "custom-chat",
                        "assistant.llm.timeout-seconds", "45"));

        assertThat(properties.ollamaBaseUrl()).isEqualTo("http://ollama.test:11434");
        assertThat(properties.modelName()).isEqualTo("custom-chat");
        assertThat(properties.timeoutSeconds()).isEqualTo(45);
    }

    @Test
    void rejectsBlankOllamaBaseUrl() {
        assertThatThrownBy(() -> bind(
                "assistant.llm",
                AssistantLlmProperties.class,
                Map.of("assistant.llm.ollama-base-url", "   ")))
                .hasMessageContaining("assistant.llm")
                .hasStackTraceContaining("ollamaBaseUrl");
    }

    @Test
    void rejectsBlankModelName() {
        assertThatThrownBy(() -> bind(
                "assistant.llm",
                AssistantLlmProperties.class,
                Map.of(
                        "assistant.llm.ollama-base-url", "http://localhost:11434",
                        "assistant.llm.model-name", "   ")))
                .hasMessageContaining("assistant.llm")
                .hasStackTraceContaining("modelName");
    }
}
