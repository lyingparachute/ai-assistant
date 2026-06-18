package dev.localassistant.assistant.answering.api.http;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static dev.localassistant.assistant.support.PropertiesBinding.bind;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssistantCorsPropertiesTest {

    @Test
    void appliesDefaultOriginWhenUnset() {
        AssistantCorsProperties properties = bind(
                "assistant.cors", AssistantCorsProperties.class, Map.of());

        assertThat(properties.allowedOrigins()).containsExactly("http://localhost:4321");
    }

    @Test
    void bindsPopulatedRoundTrip() {
        AssistantCorsProperties properties = bind(
                "assistant.cors",
                AssistantCorsProperties.class,
                Map.of(
                        "assistant.cors.allowed-origins[0]", "https://app.test",
                        "assistant.cors.allowed-origins[1]", "https://admin.test"));

        assertThat(properties.allowedOrigins())
                .containsExactly("https://app.test", "https://admin.test");
    }

    @Test
    void returnedListIsImmutable() {
        AssistantCorsProperties properties = bind(
                "assistant.cors",
                AssistantCorsProperties.class,
                Map.of("assistant.cors.allowed-origins[0]", "https://app.test"));

        List<String> origins = properties.allowedOrigins();

        assertThat(origins.getClass().getName()).contains("ImmutableCollections");
    }

    @Test
    void rejectsEmptyAllowedOrigins() {
        assertThatThrownBy(() -> bind(
                "assistant.cors",
                AssistantCorsProperties.class,
                Map.of("assistant.cors.allowed-origins", "")))
                .hasMessageContaining("assistant.cors")
                .hasStackTraceContaining("allowedOrigins");
    }
}
