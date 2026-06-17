package dev.localassistant.assistant.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "assistant.embedding")
public record AssistantEmbeddingProperties(
        @NotBlank String ollamaBaseUrl,
        @NotBlank @DefaultValue("nomic-embed-text") String modelName,
        @NotBlank @DefaultValue("search_document:") String documentPrefix,
        @NotBlank @DefaultValue("search_query:") String queryPrefix) {

    public AssistantEmbeddingProperties {
        ollamaBaseUrl = ollamaBaseUrl.strip();
        modelName = modelName.strip();
    }
}
