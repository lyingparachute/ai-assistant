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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@Profile("!test & !ingest-rag")
final class StdioMcpToolInvoker implements McpToolInvoker, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(StdioMcpToolInvoker.class);

    private static final List<String> WEATHER_ENV_PASSTHROUGH = List.of("WEATHER_API_KEY", "WEATHER_API_URL");

    private final Map<String, McpSyncClient> clientsByToolName;

    StdioMcpToolInvoker(AssistantMcpProperties assistantMcpProperties, ObjectMapper objectMapper) {
        this(Map.of(
                assistantMcpProperties.countries().toolName(),
                createClient(assistantMcpProperties.countries(), objectMapper),
                assistantMcpProperties.weather().toolName(),
                createClient(assistantMcpProperties.weather(), objectMapper)
        ));
    }

    StdioMcpToolInvoker(Map<String, McpSyncClient> clientsByToolName) {
        this.clientsByToolName = Map.copyOf(clientsByToolName);
    }

    @Override
    public McpToolResponse invoke(String toolName, Map<String, Object> arguments) {
        McpSyncClient client = clientsByToolName.get(toolName);
        if (client == null) {
            throw new McpToolInvocationException("no MCP client configured for tool " + toolName);
        }

        try {
            McpSchema.CallToolResult result = client.callTool(
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

    @Override
    public void destroy() {
        for (McpSyncClient client : distinctClients()) {
            try {
                client.closeGracefully();
            } catch (RuntimeException exception) {
                log.warn("Failed to close MCP client gracefully on shutdown", exception);
            }
        }
    }

    private Set<McpSyncClient> distinctClients() {
        Set<McpSyncClient> distinct = Collections.newSetFromMap(new IdentityHashMap<>());
        distinct.addAll(clientsByToolName.values());
        return distinct;
    }

    private static McpSyncClient createClient(AssistantMcpProperties.McpServer server, ObjectMapper objectMapper) {
        if (!"stdio".equalsIgnoreCase(server.transport())) {
            throw new McpToolInvocationException("unsupported MCP transport: " + server.transport());
        }
        if (server.command().isBlank()) {
            throw new McpToolInvocationException("MCP server command is not configured");
        }

        ServerParameters parameters = ServerParameters.builder(server.command())
                .args(server.args().toArray(String[]::new))
                .env(resolveProcessEnv(server))
                .build();
        McpJsonMapper mcpJsonMapper = new JacksonMcpJsonMapper(objectMapper);
        StdioClientTransport transport = new StdioClientTransport(parameters, mcpJsonMapper);
        McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(server.timeoutSeconds()))
                .initializationTimeout(Duration.ofSeconds(server.timeoutSeconds()))
                .build();
        client.initialize();
        return client;
    }

    private static Map<String, String> resolveProcessEnv(AssistantMcpProperties.McpServer server) {
        Map<String, String> env = new java.util.LinkedHashMap<>(server.env());
        if (assistantMcpWeatherTool(server)) {
            for (String key : WEATHER_ENV_PASSTHROUGH) {
                if (!env.containsKey(key) || env.get(key).isBlank()) {
                    String hostValue = System.getenv(key);
                    if (hostValue != null && !hostValue.isBlank()) {
                        env.put(key, hostValue);
                    }
                }
            }
        }
        return Map.copyOf(env);
    }

    private static boolean assistantMcpWeatherTool(AssistantMcpProperties.McpServer server) {
        return "get-weather".equals(server.toolName());
    }
}
