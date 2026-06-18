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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public final class CountryLookupTool {

    private final LookupCountryUseCase lookupCountryUseCase;
    private final ObjectMapper objectMapper;

    public McpSchema.Tool toolDefinition() {
        return McpSchema.Tool.builder()
                .name(CountryToolSchemas.TOOL_NAME)
                .description("Look up country facts by English country name or capital city name.")
                .inputSchema(CountryToolSchemas.inputSchema())
                .build();
    }

    public McpSchema.CallToolResult handle(McpSyncServerExchange exchange, McpSchema.CallToolRequest request) {
        Object rawName = request.arguments().get("name");
        final var name = StringUtils.stripToNull(rawName == null ? null : rawName.toString());
        if (name == null) {
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
