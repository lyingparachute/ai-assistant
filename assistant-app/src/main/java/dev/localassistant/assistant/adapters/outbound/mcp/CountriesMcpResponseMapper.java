package dev.localassistant.assistant.adapters.outbound.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.localassistant.assistant.tools.CountryInfo;
import dev.localassistant.assistant.tools.ToolExecutionResult;

import java.util.Optional;

final class CountriesMcpResponseMapper {

    static final String SOURCE_LABEL = "countries MCP";
    static final String MALFORMED_PAYLOAD_MESSAGE = "malformed countries tool payload";
    static final String MALFORMED_PAYLOAD_HINT =
            "The countries MCP server returned an unexpected payload. Retry later; do not invent country facts.";
    static final String ERROR_NAME_REQUIRED = "name is required";
    static final String HINT_NAME_REQUIRED = "Provide a non-empty English country or capital city name.";

    private final ObjectMapper objectMapper;

    CountriesMcpResponseMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    ToolExecutionResult<CountryInfo> mapBlankName() {
        return new ToolExecutionResult.ToolError<>(ERROR_NAME_REQUIRED, HINT_NAME_REQUIRED);
    }

    ToolExecutionResult<CountryInfo> mapResponse(McpToolInvoker.McpToolResponse response) {
        String payload = response.firstTextContent();
        if (payload.isBlank()) {
            return sourceUnavailable("countries MCP returned empty tool content");
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root.path("ok").asBoolean(false)) {
                return mapSuccess(root.path("data"));
            }
            return mapToolError(root);
        } catch (Exception exception) {
            return sourceUnavailable("countries MCP returned unparseable JSON");
        }
    }

    private ToolExecutionResult<CountryInfo> mapSuccess(JsonNode data) {
        Optional<CountryInfo> countryInfo = readCountryInfo(data);
        if (countryInfo.isEmpty()) {
            return new ToolExecutionResult.SourceUnavailable<>(
                    SOURCE_LABEL,
                    MALFORMED_PAYLOAD_MESSAGE,
                    MALFORMED_PAYLOAD_HINT
            );
        }
        return new ToolExecutionResult.Success<>(countryInfo.get());
    }

    private ToolExecutionResult<CountryInfo> mapToolError(JsonNode root) {
        String error = textOrDefault(root.path("error"), "country lookup failed");
        String hint = textOrDefault(root.path("hint"), "Retry with a valid English country or capital city name.");
        return new ToolExecutionResult.ToolError<>(error, hint);
    }

    private Optional<CountryInfo> readCountryInfo(JsonNode data) {
        if (!data.hasNonNull("countryName")
                || !data.hasNonNull("capital")
                || !data.hasNonNull("region")
                || !data.hasNonNull("population")) {
            return Optional.empty();
        }

        String countryName = data.path("countryName").asText();
        String capital = data.path("capital").asText();
        String region = data.path("region").asText();
        if (countryName.isBlank() || capital.isBlank() || region.isBlank()) {
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

    ToolExecutionResult<CountryInfo> mapTransportFailure(String message) {
        return sourceUnavailable(message);
    }

    private ToolExecutionResult<CountryInfo> sourceUnavailable(String message) {
        return new ToolExecutionResult.SourceUnavailable<>(
                SOURCE_LABEL,
                message,
                "The countries MCP server could not be reached. Retry later; do not invent country facts."
        );
    }

    private static String textOrDefault(JsonNode node, String fallback) {
        String text = node.asText();
        return text.isBlank() ? fallback : text;
    }
}
