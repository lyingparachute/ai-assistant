package dev.localassistant.assistant.countryfacts.infrastructure;

import dev.localassistant.assistant.countryfacts.domain.CountryInfo;
import dev.localassistant.assistant.countryfacts.domain.port.inbound.ResolveCountryFacts;
import dev.localassistant.assistant.shared.ToolExecutionResult;
import dev.localassistant.assistant.shared.mcp.McpToolInvocationException;
import dev.localassistant.assistant.shared.mcp.McpToolInvoker;
import dev.localassistant.assistant.shared.mcp.infrastructure.config.AssistantMcpProperties;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Profile("!ingest-rag")
@RequiredArgsConstructor
final class CountriesMcpClientAdapter implements ResolveCountryFacts {

    private final McpToolInvoker mcpToolInvoker;
    private final CountriesMcpResponseMapper responseMapper;
    private final AssistantMcpProperties assistantMcpProperties;

    @Override
    public ToolExecutionResult<CountryInfo> execute(final ResolveCountryFacts.Command command) {
        final var name = command.name();
        try {
            final var response = mcpToolInvoker.invoke(
                assistantMcpProperties.countries().toolName(),
                Map.of("name", StringUtils.trim(name))
            );
            return responseMapper.mapResponse(response);
        } catch (McpToolInvocationException exception) {
            return responseMapper.mapTransportFailure(exception.getMessage());
        }
    }
}
