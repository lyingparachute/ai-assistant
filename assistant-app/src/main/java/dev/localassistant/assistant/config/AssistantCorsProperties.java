package dev.localassistant.assistant.config;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "assistant.cors")
public class AssistantCorsProperties {

    @NotEmpty
    private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:4321"));

    public List<String> allowedOrigins() {
        return List.copyOf(allowedOrigins);
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = new ArrayList<>(allowedOrigins);
    }
}
