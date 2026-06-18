package dev.localassistant.assistant.shared.mcp.infrastructure.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static dev.localassistant.assistant.shared.mcp.infrastructure.config.AssistantMcpProperties.McpServer;
import static dev.localassistant.assistant.support.PropertiesBinding.bind;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssistantMcpPropertiesTest {

    @Test
    void appliesNestedServerDefaultsWhenOnlyRequiredValuesPresent() {
        AssistantMcpProperties properties = bind(
                "assistant.mcp",
                AssistantMcpProperties.class,
                Map.of(
                        "assistant.mcp.countries.command", "java",
                        "assistant.mcp.countries.tool-name", "country_lookup",
                        "assistant.mcp.weather.command", "mcp-weather",
                        "assistant.mcp.weather.tool-name", "get-weather"));

        McpServer countries = properties.countries();
        assertThat(countries.command()).isEqualTo("java");
        assertThat(countries.toolName()).isEqualTo("country_lookup");
        assertThat(countries.transport()).isEqualTo("stdio");
        assertThat(countries.timeoutSeconds()).isEqualTo(60);
        assertThat(countries.args()).isEmpty();
        assertThat(countries.env()).isEmpty();
    }

    @Test
    void bindsPopulatedNestedArgsAndEnvRoundTrip() {
        AssistantMcpProperties properties = bind(
                "assistant.mcp",
                AssistantMcpProperties.class,
                Map.of(
                        "assistant.mcp.countries.command", "java",
                        "assistant.mcp.countries.tool-name", "country_lookup",
                        "assistant.mcp.countries.args[0]", "-jar",
                        "assistant.mcp.countries.args[1]", "countries.jar",
                        "assistant.mcp.countries.env.REST_URL", "https://restcountries.com/v3.1",
                        "assistant.mcp.countries.transport", "stdio",
                        "assistant.mcp.countries.timeout-seconds", "45",
                        "assistant.mcp.weather.command", "mcp-weather",
                        "assistant.mcp.weather.tool-name", "get-weather"));

        McpServer countries = properties.countries();
        assertThat(countries.args()).containsExactly("-jar", "countries.jar");
        assertThat(countries.env()).containsEntry("REST_URL", "https://restcountries.com/v3.1");
        assertThat(countries.timeoutSeconds()).isEqualTo(45);
    }

    @Test
    void nestedArgsAndEnvAreImmutable() {
        AssistantMcpProperties properties = bind(
                "assistant.mcp",
                AssistantMcpProperties.class,
                Map.of(
                        "assistant.mcp.countries.command", "java",
                        "assistant.mcp.countries.tool-name", "country_lookup",
                        "assistant.mcp.countries.args[0]", "-jar",
                        "assistant.mcp.countries.env.REST_URL", "https://example.test",
                        "assistant.mcp.weather.command", "mcp-weather",
                        "assistant.mcp.weather.tool-name", "get-weather"));

        assertThatThrownBy(() -> properties.countries().args().add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> properties.countries().env().put("k", "v"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
