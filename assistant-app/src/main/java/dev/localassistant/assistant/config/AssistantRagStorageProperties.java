package dev.localassistant.assistant.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * pgvector connection settings. The chunk table name is fixed to {@code rag_chunks} for M2; it is
 * not configurable through these properties.
 */
@Validated
@ConfigurationProperties(prefix = "assistant.rag")
public record AssistantRagStorageProperties(
        @NotBlank String jdbcUrl,
        @NotBlank String username,
        @NotBlank String password) {
}
