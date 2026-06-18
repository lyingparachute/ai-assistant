package dev.localassistant.assistant.answering.api.http;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "assistant.chat")
record AssistantChatProperties(
    @Positive @DefaultValue("150") int streamTimeoutSeconds,
    @Positive @DefaultValue("4") int poolSize,
    @Positive @DefaultValue("32") int queueCapacity) {
}
