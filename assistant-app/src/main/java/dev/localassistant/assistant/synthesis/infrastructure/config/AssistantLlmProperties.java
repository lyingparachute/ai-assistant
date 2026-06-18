package dev.localassistant.assistant.synthesis.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "assistant.llm")
record AssistantLlmProperties(
    @NotBlank String ollamaBaseUrl,
    @NotBlank @DefaultValue("qwen3:4b") String modelName,
    @Positive @DefaultValue("120") int timeoutSeconds) {

    AssistantLlmProperties {
        ollamaBaseUrl = ollamaBaseUrl.strip();
        modelName = modelName.strip();
    }
}
