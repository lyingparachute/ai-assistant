package dev.localassistant.countries.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.localassistant.countries.application.LookupCountryUseCase;
import dev.localassistant.countries.model.CountryLookupOutcome;
import dev.localassistant.countries.model.LookupPlace;
import dev.localassistant.countries.schemas.CountryToolSchemas;
import dev.localassistant.countries.support.errors.CountryToolErrors;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public final class CountryLookupTool {

    private static final Logger log = LoggerFactory.getLogger(CountryLookupTool.class);

    private final LookupCountryUseCase lookupCountryUseCase;
    private final ObjectMapper objectMapper;

    public CountryLookupTool(LookupCountryUseCase lookupCountryUseCase, ObjectMapper objectMapper) {
        this.lookupCountryUseCase = lookupCountryUseCase;
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

        CountryLookupOutcome outcome = lookupCountryUseCase.lookup(LookupPlace.of(name));
        CountryToolResult.ToolEnvelope envelope = CountryToolResult.fromOutcome(outcome);
        return toCallToolResult(envelope.payload(), envelope.isError());
    }

    private McpSchema.CallToolResult toCallToolResult(Map<String, Object> payload, boolean isError) {
        try {
            return textResult(objectMapper.writeValueAsString(payload), isError);
        } catch (JsonProcessingException exception) {
            log.error("Failed to serialize country tool result", exception);
            return textResult(CountryToolErrors.internalFailureJson(), true);
        }
    }

    private static McpSchema.CallToolResult textResult(String json, boolean isError) {
        return McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent(json)))
                .isError(isError)
                .build();
    }
}
