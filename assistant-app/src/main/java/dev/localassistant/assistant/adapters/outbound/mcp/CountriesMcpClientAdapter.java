package dev.localassistant.assistant.adapters.outbound.mcp;

import dev.localassistant.assistant.config.AssistantMcpProperties;
import dev.localassistant.assistant.tools.CountriesPort;
import dev.localassistant.assistant.tools.CountryInfo;
import dev.localassistant.assistant.tools.ToolExecutionResult;

import java.util.Map;

public final class CountriesMcpClientAdapter implements CountriesPort {

    private final McpToolInvoker mcpToolInvoker;
    private final CountriesMcpResponseMapper responseMapper;
    private final String toolName;

    public CountriesMcpClientAdapter(
            McpToolInvoker mcpToolInvoker,
            CountriesMcpResponseMapper responseMapper,
            AssistantMcpProperties assistantMcpProperties
    ) {
        this.mcpToolInvoker = mcpToolInvoker;
        this.responseMapper = responseMapper;
        this.toolName = assistantMcpProperties.countries().toolName();
    }

    @Override
    public ToolExecutionResult<CountryInfo> lookupCountry(String name) {
        if (name == null || name.isBlank()) {
            return responseMapper.mapBlankName();
        }

        try {
            McpToolInvoker.McpToolResponse response = mcpToolInvoker.invoke(
                    toolName,
                    Map.of("name", name.trim())
            );
            return responseMapper.mapResponse(response);
        } catch (McpToolInvocationException exception) {
            return responseMapper.mapTransportFailure(exception.getMessage());
        }
    }
}
