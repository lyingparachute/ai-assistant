package dev.localassistant.assistant.answering.api.http;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties(prefix = "assistant.cors")
record AssistantCorsProperties(
    @NotEmpty @DefaultValue("http://localhost:4321") List<String> allowedOrigins,
    @NotEmpty @DefaultValue({"POST", "OPTIONS"}) List<String> allowedMethods,
    @NotEmpty @DefaultValue("Content-Type") List<String> allowedHeaders) {

    AssistantCorsProperties {
        allowedOrigins = List.copyOf(allowedOrigins);
        allowedMethods = List.copyOf(allowedMethods);
        allowedHeaders = List.copyOf(allowedHeaders);
    }
}
