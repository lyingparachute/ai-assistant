package dev.localassistant.assistant.weather.infrastructure;

import dev.localassistant.assistant.shared.ToolExecutionResult;
import dev.localassistant.assistant.shared.mcp.McpToolInvocationException;
import dev.localassistant.assistant.shared.mcp.McpToolInvoker;
import dev.localassistant.assistant.shared.mcp.infrastructure.config.AssistantMcpProperties;
import dev.localassistant.assistant.weather.domain.WeatherReport;
import dev.localassistant.assistant.weather.domain.port.inbound.ResolveWeatherObservation;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Profile("!ingest-rag")
@RequiredArgsConstructor
final class WeatherMcpClientAdapter implements ResolveWeatherObservation {

    private final McpToolInvoker mcpToolInvoker;
    private final WeatherMcpResponseMapper responseMapper;
    private final AssistantMcpProperties assistantMcpProperties;

    @Override
    public ToolExecutionResult<WeatherReport> execute(final ResolveWeatherObservation.Command command) {
        final var canonicalLocation = StringUtils.trim(command.location().city());
        try {
            final var response = mcpToolInvoker.invoke(
                assistantMcpProperties.weather().toolName(),
                Map.of("city", canonicalLocation)
            );
            return responseMapper.mapResponse(canonicalLocation, response);
        } catch (McpToolInvocationException exception) {
            return responseMapper.mapTransportFailure(exception.getMessage());
        }
    }
}
