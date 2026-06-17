package dev.localassistant.assistant.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Deterministic chunking window bound from {@code assistant.rag}.
 * <p>
 * The cross-field invariant is validated at the config boundary so a misconfiguration fails
 * context startup with a key-named message. {@link dev.localassistant.assistant.rag.DeterministicTextChunker}
 * keeps the same invariant as a value-object constructor guard (defense in depth): it is also
 * constructed directly in tests, so it must stay valid by construction.
 */
@Validated
@ConfigurationProperties(prefix = "assistant.rag")
public record AssistantRagChunkingProperties(
        @Positive @DefaultValue("1000") int chunkMaxSize,
        @PositiveOrZero @DefaultValue("200") int chunkOverlap) {

    @AssertTrue(message = "assistant.rag.chunk-overlap must be smaller than assistant.rag.chunk-max-size")
    public boolean isOverlapSmallerThanMax() {
        return chunkOverlap < chunkMaxSize;
    }
}
