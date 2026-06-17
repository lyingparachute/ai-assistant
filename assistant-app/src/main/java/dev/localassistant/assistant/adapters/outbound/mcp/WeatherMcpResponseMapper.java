package dev.localassistant.assistant.adapters.outbound.mcp;

import java.time.Clock;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.localassistant.assistant.tools.Location;
import dev.localassistant.assistant.tools.Temperature;
import dev.localassistant.assistant.tools.ToolExecutionResult;
import dev.localassistant.assistant.tools.WeatherReport;
import dev.localassistant.assistant.tools.WeatherTimestamp;

final class WeatherMcpResponseMapper {

    static final String SOURCE_LABEL = "weather MCP";
    static final String PROVIDER_ERROR_TEXT = "Some error occured.";
    static final String MALFORMED_PAYLOAD_MESSAGE = "malformed weather tool payload";
    static final String MALFORMED_PAYLOAD_HINT =
            "The weather MCP server returned an unexpected payload. Retry later; do not invent temperatures.";
    static final String ERROR_LOCATION_REQUIRED = "location is required";
    static final String HINT_LOCATION_REQUIRED = "Provide a non-empty city name such as Munich.";
    static final String MCP_ERROR_FLAG_MESSAGE = "weather MCP reported a tool error with unrecognized content";

    private static final Pattern SUCCESS_PATTERN = Pattern.compile(
            "^the weather in (.+) is currently:\\s*(-?\\d+(?:\\.\\d+)?)\\s*$",
            Pattern.CASE_INSENSITIVE
    );

    private final Clock clock;

    WeatherMcpResponseMapper(Clock clock) {
        this.clock = clock;
    }

    ToolExecutionResult<WeatherReport> mapBlankLocation() {
        return new ToolExecutionResult.ToolError<>(ERROR_LOCATION_REQUIRED, HINT_LOCATION_REQUIRED);
    }

    ToolExecutionResult<WeatherReport> mapResponse(String requestedLocation, McpToolInvoker.McpToolResponse response) {
        String payload = response.firstTextContent();
        if (payload.isBlank()) {
            return sourceUnavailable("weather MCP returned empty tool content");
        }

        if (PROVIDER_ERROR_TEXT.equalsIgnoreCase(payload.trim())) {
            return sourceUnavailable("weather provider returned an error");
        }

        Matcher matcher = SUCCESS_PATTERN.matcher(payload.trim());
        if (!matcher.matches()) {
            if (response.isError()) {
                return sourceUnavailable(MCP_ERROR_FLAG_MESSAGE);
            }
            return new ToolExecutionResult.SourceUnavailable<>(
                    SOURCE_LABEL,
                    MALFORMED_PAYLOAD_MESSAGE,
                    MALFORMED_PAYLOAD_HINT
            );
        }

        double temperatureCelsius = Double.parseDouble(matcher.group(2));
        Location location = new Location(requestedLocation);
        Instant retrievedAt = clock.instant();
        WeatherReport report = new WeatherReport(
                location,
                Temperature.celsius(temperatureCelsius),
                new WeatherTimestamp.Retrieved(retrievedAt)
        );
        return new ToolExecutionResult.Success<>(report);
    }

    ToolExecutionResult<WeatherReport> mapTransportFailure(String message) {
        return sourceUnavailable(message);
    }

    private ToolExecutionResult<WeatherReport> sourceUnavailable(String message) {
        return new ToolExecutionResult.SourceUnavailable<>(
                SOURCE_LABEL,
                message,
                "The weather MCP server could not be reached. Retry later; do not invent temperatures."
        );
    }
}
