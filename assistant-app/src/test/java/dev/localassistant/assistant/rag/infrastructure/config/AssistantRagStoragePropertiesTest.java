package dev.localassistant.assistant.rag.infrastructure.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static dev.localassistant.assistant.support.PropertiesBinding.bind;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssistantRagStoragePropertiesTest {

    @Test
    void bindsPopulatedRoundTrip() {
        AssistantRagStorageProperties properties = bind(
                "assistant.rag",
                AssistantRagStorageProperties.class,
                Map.of(
                        "assistant.rag.jdbc-url", "jdbc:postgresql://localhost:5432/assistant_rag",
                        "assistant.rag.username", "assistant",
                        "assistant.rag.password", "secret"));

        assertThat(properties.jdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/assistant_rag");
        assertThat(properties.username()).isEqualTo("assistant");
        assertThat(properties.password()).isEqualTo("secret");
    }

    @Test
    void rejectsMissingJdbcUrl() {
        assertThatThrownBy(() -> bind(
                "assistant.rag",
                AssistantRagStorageProperties.class,
                Map.of(
                        "assistant.rag.username", "assistant",
                        "assistant.rag.password", "secret")))
                .hasMessageContaining("assistant.rag")
                .hasStackTraceContaining("jdbcUrl");
    }
}
