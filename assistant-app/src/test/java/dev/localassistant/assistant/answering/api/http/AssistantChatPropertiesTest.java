package dev.localassistant.assistant.answering.api.http;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static dev.localassistant.assistant.support.PropertiesBinding.bind;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssistantChatPropertiesTest {

    @Test
    void appliesDefaultsWhenPrefixPresentWithNoValues() {
        AssistantChatProperties properties =
                bind("assistant.chat", AssistantChatProperties.class, Map.of());

        assertThat(properties.streamTimeoutSeconds()).isEqualTo(150);
        assertThat(properties.poolSize()).isEqualTo(4);
        assertThat(properties.queueCapacity()).isEqualTo(32);
    }

    @Test
    void bindsPopulatedRoundTrip() {
        AssistantChatProperties properties =
                bind(
                        "assistant.chat",
                        AssistantChatProperties.class,
                        Map.of(
                                "assistant.chat.stream-timeout-seconds", "300",
                                "assistant.chat.pool-size", "8",
                                "assistant.chat.queue-capacity", "64"));

        assertThat(properties.streamTimeoutSeconds()).isEqualTo(300);
        assertThat(properties.poolSize()).isEqualTo(8);
        assertThat(properties.queueCapacity()).isEqualTo(64);
    }

    @Test
    void rejectsNonPositiveStreamTimeout() {
        assertThatThrownBy(() -> bind(
                        "assistant.chat",
                        AssistantChatProperties.class,
                        Map.of("assistant.chat.stream-timeout-seconds", "0")))
                .hasMessageContaining("assistant.chat")
                .hasStackTraceContaining("streamTimeoutSeconds");
    }

    @Test
    void rejectsNonPositivePoolSize() {
        assertThatThrownBy(() -> bind(
                        "assistant.chat",
                        AssistantChatProperties.class,
                        Map.of("assistant.chat.pool-size", "-1")))
                .hasMessageContaining("assistant.chat")
                .hasStackTraceContaining("poolSize");
    }

    @Test
    void rejectsNonPositiveQueueCapacity() {
        assertThatThrownBy(() -> bind(
                        "assistant.chat",
                        AssistantChatProperties.class,
                        Map.of("assistant.chat.queue-capacity", "0")))
                .hasMessageContaining("assistant.chat")
                .hasStackTraceContaining("queueCapacity");
    }
}
