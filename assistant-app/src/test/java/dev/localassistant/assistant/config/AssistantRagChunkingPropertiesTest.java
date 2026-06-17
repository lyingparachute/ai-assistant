package dev.localassistant.assistant.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

import static dev.localassistant.assistant.config.PropertiesBinding.bind;
import static org.assertj.core.api.Assertions.assertThat;

class AssistantRagChunkingPropertiesTest {

    private static final String CROSS_FIELD_MESSAGE =
            "assistant.rag.chunk-overlap must be smaller than assistant.rag.chunk-max-size";

    @Test
    void appliesDefaultsWhenUnset() {
        AssistantRagChunkingProperties properties = bind(
                "assistant.rag", AssistantRagChunkingProperties.class, Map.of());

        assertThat(properties.chunkMaxSize()).isEqualTo(1000);
        assertThat(properties.chunkOverlap()).isEqualTo(200);
    }

    @Test
    void bindsPopulatedRoundTrip() {
        AssistantRagChunkingProperties properties = bind(
                "assistant.rag",
                AssistantRagChunkingProperties.class,
                Map.of(
                        "assistant.rag.chunk-max-size", "500",
                        "assistant.rag.chunk-overlap", "50"));

        assertThat(properties.chunkMaxSize()).isEqualTo(500);
        assertThat(properties.chunkOverlap()).isEqualTo(50);
    }

    @Test
    void misconfiguredOverlapFailsContextStartupWithBothKeysNamed() {
        new ApplicationContextRunner()
                .withUserConfiguration(ChunkingPropertiesHolder.class)
                .withPropertyValues(
                        "assistant.rag.chunk-max-size=200",
                        "assistant.rag.chunk-overlap=200")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining(CROSS_FIELD_MESSAGE));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AssistantRagChunkingProperties.class)
    static class ChunkingPropertiesHolder {
    }
}
