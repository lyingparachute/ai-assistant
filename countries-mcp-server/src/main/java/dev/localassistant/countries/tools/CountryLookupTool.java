package dev.localassistant.countries.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.localassistant.countries.application.CountriesApplicationService;
import dev.localassistant.countries.model.CountryLookupOutcome;
import dev.localassistant.countries.schemas.CountryToolSchemas;
import dev.localassistant.countries.support.errors.CountryToolErrors;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;
import java.util.Map;

public final class CountryLookupTool {

    private final CountriesApplicationService countriesApplicationService;
    private final ObjectMapper objectMapper;

    public CountryLookupTool(CountriesApplicationService countriesApplicationService, ObjectMapper objectMapper) {
        this.countriesApplicationService = countriesApplicationService;
        this.objectMapper = objectMapper;
    }

    public McpSchema.Tool toolDefinition() {
        return McpSchema.Tool.builder()
                .name(CountryToolSchemas.TOOL_NAME)
                .description("Look up country facts by English country name or capital city name.")
                .inputSchema(CountryToolSchemas.inputSchema())
                .build();
    }

    public McpSchema.CallToolResult handle(McpSyncServerExchange exchange, McpSchema.CallToolRequest request) {
        Object rawName = request.arguments().get("name");
        String name = rawName == null ? "" : rawName.toString();
        if (name.isBlank()) {
            return toCallToolResult(CountryToolErrors.nameRequired(), true);
        }

        CountryLookupOutcome outcome = countriesApplicationService.lookupCountry(name);
        return toCallToolResult(CountryToolResult.fromOutcome(outcome), CountryToolResult.isErrorOutcome(outcome));
    }

    private McpSchema.CallToolResult toCallToolResult(Map<String, Object> payload, boolean isError) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent(json)))
                    .isError(isError)
                    .build();
        } catch (JsonProcessingException exception) {
            return toCallToolResult(CountryToolErrors.internalFailure(), true);
        }
    }
}
