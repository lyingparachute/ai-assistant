package dev.localassistant.assistant.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Validated
@ConfigurationProperties(prefix = "assistant.mcp")
public class AssistantMcpProperties {

    @Valid
    @NotNull
    private McpServer countries = new McpServer();

    @Valid
    @NotNull
    private McpServer weather = new McpServer();

    public McpServer countries() {
        return countries;
    }

    public void setCountries(McpServer countries) {
        this.countries = countries;
    }

    public McpServer weather() {
        return weather;
    }

    public void setWeather(McpServer weather) {
        this.weather = weather;
    }

    public static class McpServer {

        @NotBlank
        private String command = "";

        @NotNull
        private List<String> args = new ArrayList<>();

        @NotNull
        private Map<String, String> env = new LinkedHashMap<>();

        @NotBlank
        private String workingDirectory = ".";

        @NotBlank
        private String transport = "stdio";

        @Positive
        private int timeoutSeconds = 60;

        @NotBlank
        private String toolName = "";

        public String command() {
            return command;
        }

        public void setCommand(String command) {
            this.command = command;
        }

        public List<String> args() {
            return List.copyOf(args);
        }

        public void setArgs(List<String> args) {
            this.args = new ArrayList<>(args);
        }

        public Map<String, String> env() {
            return Map.copyOf(env);
        }

        public void setEnv(Map<String, String> env) {
            this.env = new LinkedHashMap<>(env);
        }

        public String workingDirectory() {
            return workingDirectory;
        }

        public void setWorkingDirectory(String workingDirectory) {
            this.workingDirectory = workingDirectory;
        }

        public String transport() {
            return transport;
        }

        public void setTransport(String transport) {
            this.transport = transport;
        }

        public int timeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public String toolName() {
            return toolName;
        }

        public void setToolName(String toolName) {
            this.toolName = toolName;
        }
    }
}
