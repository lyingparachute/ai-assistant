package dev.localassistant.assistant.adapters.outbound.mcp;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class WeatherMcpClientContractTest {

    private static final String TOOL_NAME = "get-weather";
    private static final Instant FIXED_INSTANT = Instant.parse("2026-06-15T10:15:30Z");
    private final WeatherMcpResponseMapper mapper = new WeatherMcpResponseMapper(
            Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC)
    );

    @Test
    void pinsToolNameAndRequiredInputField() {
        assertThat(TOOL_NAME).isEqualTo("get-weather");
    }

    @Test
    void munichSuccessMapsToWeatherReportWithRetrievedTimestamp() {
        String payload = "the weather in Munich is currently: 12.3";

        var result = mapper.mapResponse(
                "Munich",
                new McpToolInvoker.McpToolResponse(java.util.List.of(payload), false)
        );

        assertThat(result).isInstanceOf(dev.localassistant.assistant.tools.ToolExecutionResult.Success.class);
        var success = (dev.localassistant.assistant.tools.ToolExecutionResult.Success<
                dev.localassistant.assistant.tools.WeatherReport>) result;
        assertThat(success.value().location().city()).isEqualTo("Munich");
        assertThat(success.value().temperature().celsius()).isEqualTo(12.3);
        assertThat(success.value().timestamp()).isInstanceOf(
                dev.localassistant.assistant.tools.WeatherTimestamp.Retrieved.class
        );
        assertThat(success.value().timestamp().value()).isEqualTo(FIXED_INSTANT);
    }

    @Test
    void providerErrorTextMapsToSourceUnavailable() {
        var result = mapper.mapResponse(
                "Munich",
                new McpToolInvoker.McpToolResponse(java.util.List.of("Some error occured."), false)
        );

        assertThat(result).isInstanceOf(dev.localassistant.assistant.tools.ToolExecutionResult.SourceUnavailable.class);
    }

    @Test
    void mcpErrorFlagWithUnrecognizedContentMapsToSourceUnavailable() {
        var result = mapper.mapResponse(
                "Munich",
                new McpToolInvoker.McpToolResponse(java.util.List.of("upstream crashed"), true)
        );

        assertThat(result).isInstanceOf(dev.localassistant.assistant.tools.ToolExecutionResult.SourceUnavailable.class);
        var unavailable = (dev.localassistant.assistant.tools.ToolExecutionResult.SourceUnavailable<
                dev.localassistant.assistant.tools.WeatherReport>) result;
        assertThat(unavailable.sourceLabel()).isEqualTo(WeatherMcpResponseMapper.SOURCE_LABEL);
        assertThat(unavailable.message()).isEqualTo(WeatherMcpResponseMapper.MCP_ERROR_FLAG_MESSAGE);
    }

    @Test
    void malformedPayloadMapsToSourceUnavailable() {
        var result = mapper.mapResponse(
                "Munich",
                new McpToolInvoker.McpToolResponse(java.util.List.of("unexpected payload"), false)
        );

        assertThat(result).isInstanceOf(dev.localassistant.assistant.tools.ToolExecutionResult.SourceUnavailable.class);
        var unavailable = (dev.localassistant.assistant.tools.ToolExecutionResult.SourceUnavailable<
                dev.localassistant.assistant.tools.WeatherReport>) result;
        assertThat(unavailable.message()).isEqualTo(WeatherMcpResponseMapper.MALFORMED_PAYLOAD_MESSAGE);
    }
}
