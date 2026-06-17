package dev.localassistant.assistant.adapters.outbound.mcp;

import dev.localassistant.assistant.config.AssistantMcpProperties;
import dev.localassistant.assistant.tools.ToolExecutionResult;
import dev.localassistant.assistant.tools.WeatherPort;
import dev.localassistant.assistant.tools.WeatherReport;

import java.util.Map;

public final class WeatherMcpClientAdapter implements WeatherPort {

    private final McpToolInvoker mcpToolInvoker;
    private final WeatherMcpResponseMapper responseMapper;
    private final String toolName;

    public WeatherMcpClientAdapter(
            McpToolInvoker mcpToolInvoker,
            WeatherMcpResponseMapper responseMapper,
            AssistantMcpProperties assistantMcpProperties
    ) {
        this.mcpToolInvoker = mcpToolInvoker;
        this.responseMapper = responseMapper;
        this.toolName = assistantMcpProperties.weather().toolName();
    }

    @Override
    public ToolExecutionResult<WeatherReport> currentWeather(String location) {
        if (location == null || location.isBlank()) {
            return responseMapper.mapBlankLocation();
        }

        String canonicalLocation = location.trim();
        try {
            McpToolInvoker.McpToolResponse response = mcpToolInvoker.invoke(
                    toolName,
                    Map.of("city", canonicalLocation)
            );
            return responseMapper.mapResponse(canonicalLocation, response);
        } catch (McpToolInvocationException exception) {
            return responseMapper.mapTransportFailure(exception.getMessage());
        }
    }
}
