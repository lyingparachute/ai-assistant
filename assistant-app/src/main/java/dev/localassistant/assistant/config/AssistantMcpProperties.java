package dev.localassistant.assistant.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;

@Validated
@ConfigurationProperties(prefix = "assistant.mcp")
public record AssistantMcpProperties(
        @Valid @NotNull @DefaultValue McpServer countries,
        @Valid @NotNull @DefaultValue McpServer weather) {

    public record McpServer(
            @NotBlank String command,
            @NotNull @DefaultValue List<String> args,
            @NotNull @DefaultValue Map<String, String> env,
            @NotBlank @DefaultValue("stdio") String transport,
            @Positive @DefaultValue("60") int timeoutSeconds,
            @NotBlank String toolName) {

        public McpServer {
            args = List.copyOf(args);
            env = Map.copyOf(env);
        }
    }
}
