package dev.localassistant.assistant.countryfacts.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.localassistant.assistant.countryfacts.domain.CountryInfo;
import dev.localassistant.assistant.shared.ToolExecutionResult;
import dev.localassistant.assistant.shared.mcp.McpToolInvoker;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
final class CountriesMcpResponseMapper {

    static final String SOURCE_LABEL = "countries MCP";
    static final String MALFORMED_PAYLOAD_MESSAGE = "malformed countries tool payload";
    static final String MALFORMED_PAYLOAD_HINT =
        "The countries MCP server returned an unexpected payload. Retry later; do not invent country facts.";
    static final String ERROR_NAME_REQUIRED = "name is required";
    static final String HINT_NAME_REQUIRED = "Provide a non-empty English country or capital city name.";
    static final String MCP_ERROR_FLAG_MESSAGE = "countries MCP reported a tool error with unrecognized content";
    private static final String ENVELOPE_OK_FIELD = "ok";

    private final ObjectMapper objectMapper;

    ToolExecutionResult<CountryInfo> mapBlankName() {
        return new ToolExecutionResult.ToolError<>(ERROR_NAME_REQUIRED, HINT_NAME_REQUIRED);
    }

    ToolExecutionResult<CountryInfo> mapResponse(final McpToolInvoker.McpToolResponse response) {
        final var payload = response.firstTextContent();
        if (StringUtils.isBlank(payload)) {
            return sourceUnavailable("countries MCP returned empty tool content");
        }

        try {
            final var root = objectMapper.readTree(payload);
            if (!root.has(ENVELOPE_OK_FIELD)) {
                return response.isError()
                    ? sourceUnavailable(MCP_ERROR_FLAG_MESSAGE)
                    : sourceUnavailable("countries MCP returned an unrecognized payload");
            }

            if (root.path(ENVELOPE_OK_FIELD).asBoolean(false)) {
                return mapSuccess(root.path("data"));
            }
            return mapToolError(root);
        } catch (Exception exception) {
            return response.isError()
                ? sourceUnavailable(MCP_ERROR_FLAG_MESSAGE)
                : sourceUnavailable("countries MCP returned unparseable JSON");
        }
    }

    private ToolExecutionResult<CountryInfo> mapSuccess(final JsonNode data) {
        final var countryInfo = readCountryInfo(data);
        if (countryInfo.isEmpty()) {
            return new ToolExecutionResult.SourceUnavailable<>(
                SOURCE_LABEL,
                MALFORMED_PAYLOAD_MESSAGE,
                MALFORMED_PAYLOAD_HINT
            );
        }
        return new ToolExecutionResult.Success<>(countryInfo.get());
    }

    private ToolExecutionResult<CountryInfo> mapToolError(final JsonNode root) {
        final var error = textOrDefault(root.path("error"), "country lookup failed");
        final var hint = textOrDefault(root.path("hint"), "Retry with a valid English country or capital city name.");
        return new ToolExecutionResult.ToolError<>(error, hint);
    }

    private Optional<CountryInfo> readCountryInfo(final JsonNode data) {
        if (!data.hasNonNull("countryName")
            || !data.hasNonNull("capital")
            || !data.hasNonNull("region")
            || !data.hasNonNull("population")) {
            return Optional.empty();
        }

        final var countryName = data.path("countryName").asText();
        final var capital = data.path("capital").asText();
        final var region = data.path("region").asText();
        if (StringUtils.isAnyBlank(countryName, capital, region)) {
            return Optional.empty();
        }

        if (!data.path("population").isNumber()) {
            return Optional.empty();
        }

        try {
            return Optional.of(new CountryInfo(
                countryName,
                capital,
                region,
                data.path("population").asLong()
            ));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    ToolExecutionResult<CountryInfo> mapTransportFailure(final String message) {
        return sourceUnavailable(message);
    }

    private ToolExecutionResult<CountryInfo> sourceUnavailable(final String message) {
        return new ToolExecutionResult.SourceUnavailable<>(
            SOURCE_LABEL,
            message,
            "The countries MCP server could not be reached. Retry later; do not invent country facts."
        );
    }

    private static String textOrDefault(final JsonNode node, final String fallback) {
        final var text = node.asText();
        return StringUtils.isBlank(text) ? fallback : text;
    }
}
