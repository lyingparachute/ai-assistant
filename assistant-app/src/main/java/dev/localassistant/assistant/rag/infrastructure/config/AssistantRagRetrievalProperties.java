package dev.localassistant.assistant.rag.infrastructure.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * RAG retrieval and product-page fetch settings bound from {@code assistant.rag}.
 */
@Validated
@ConfigurationProperties(prefix = "assistant.rag")
public record AssistantRagRetrievalProperties(
    @Min(1) @DefaultValue("5") int topK,
    @DecimalMin("0.0") @DecimalMax("1.0") @DefaultValue("0.5") double relevanceThreshold,
    @NotBlank @DefaultValue("https://www.cdq.com/products/cdq-fraud-guard") String sourceUrl,
    @Min(1) @DefaultValue("30") int fetchTimeoutSeconds) {
}
