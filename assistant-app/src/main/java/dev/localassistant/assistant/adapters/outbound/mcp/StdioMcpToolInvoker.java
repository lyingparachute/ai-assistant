package dev.localassistant.assistant.adapters.outbound.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.localassistant.assistant.config.AssistantMcpProperties;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@Profile("!test")
final class StdioMcpToolInvoker implements McpToolInvoker {

    private final Map<String, ManagedClient> clientsByToolName;

    StdioMcpToolInvoker(AssistantMcpProperties assistantMcpProperties, ObjectMapper objectMapper) {
        this.clientsByToolName = Map.of(
                assistantMcpProperties.countries().toolName(),
                createClient(assistantMcpProperties.countries(), objectMapper),
                assistantMcpProperties.weather().toolName(),
                createClient(assistantMcpProperties.weather(), objectMapper)
        );
    }

    @Override
    public McpToolResponse invoke(String toolName, Map<String, Object> arguments) {
        ManagedClient managedClient = clientsByToolName.get(toolName);
        if (managedClient == null) {
            throw new McpToolInvocationException("no MCP client configured for tool " + toolName);
        }

        try {
            McpSchema.CallToolResult result = managedClient.client().callTool(
                    new McpSchema.CallToolRequest(toolName, arguments)
            );
            List<String> textContents = result.content().stream()
                    .filter(McpSchema.TextContent.class::isInstance)
                    .map(McpSchema.TextContent.class::cast)
                    .map(McpSchema.TextContent::text)
                    .toList();
            return new McpToolResponse(textContents, result.isError());
        } catch (RuntimeException exception) {
            throw new McpToolInvocationException("MCP tool call failed for " + toolName, exception);
        }
    }

    private static ManagedClient createClient(AssistantMcpProperties.McpServer server, ObjectMapper objectMapper) {
        if (!"stdio".equalsIgnoreCase(server.transport())) {
            throw new McpToolInvocationException("unsupported MCP transport: " + server.transport());
        }
        if (server.command().isBlank()) {
            throw new McpToolInvocationException("MCP server command is not configured");
        }

        ServerParameters parameters = ServerParameters.builder(server.command())
                .args(server.args().toArray(String[]::new))
                .env(server.env())
                .build();
        McpJsonMapper mcpJsonMapper = new JacksonMcpJsonMapper(objectMapper);
        StdioClientTransport transport = new StdioClientTransport(parameters, mcpJsonMapper);
        McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(server.timeoutSeconds()))
                .build();
        client.initialize();
        return new ManagedClient(client);
    }

    private record ManagedClient(McpSyncClient client) {
    }
}
