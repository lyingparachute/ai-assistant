package dev.localassistant.assistant.weather.infrastructure;

import dev.localassistant.assistant.shared.ToolExecutionResult;
import dev.localassistant.assistant.shared.mcp.McpToolInvoker;
import dev.localassistant.assistant.weather.domain.Location;
import dev.localassistant.assistant.weather.domain.Temperature;
import dev.localassistant.assistant.weather.domain.WeatherReport;
import dev.localassistant.assistant.weather.domain.WeatherTimestamp;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
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

    ToolExecutionResult<WeatherReport> mapBlankLocation() {
        return new ToolExecutionResult.ToolError<>(ERROR_LOCATION_REQUIRED, HINT_LOCATION_REQUIRED);
    }

    ToolExecutionResult<WeatherReport> mapResponse(final String requestedLocation, final McpToolInvoker.McpToolResponse response) {
        final var payload = response.firstTextContent();
        if (StringUtils.isBlank(payload)) {
            return sourceUnavailable("weather MCP returned empty tool content");
        }

        final var trimmedPayload = StringUtils.trim(payload);
        if (StringUtils.equalsIgnoreCase(trimmedPayload, PROVIDER_ERROR_TEXT)) {
            return sourceUnavailable("weather provider returned an error");
        }

        final var matcher = SUCCESS_PATTERN.matcher(trimmedPayload);
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

        final var temperatureCelsius = Double.parseDouble(matcher.group(2));
        final var location = new Location(requestedLocation);
        final var retrievedAt = clock.instant();
        final var report = new WeatherReport(
            location,
            Temperature.celsius(temperatureCelsius),
            new WeatherTimestamp.Retrieved(retrievedAt)
        );
        return new ToolExecutionResult.Success<>(report);
    }

    ToolExecutionResult<WeatherReport> mapTransportFailure(final String message) {
        return sourceUnavailable(message);
    }

    private ToolExecutionResult<WeatherReport> sourceUnavailable(final String message) {
        return new ToolExecutionResult.SourceUnavailable<>(
            SOURCE_LABEL,
            message,
            "The weather MCP server could not be reached. Retry later; do not invent temperatures."
        );
    }
}
