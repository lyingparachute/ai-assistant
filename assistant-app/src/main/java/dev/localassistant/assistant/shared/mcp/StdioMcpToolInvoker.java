package dev.localassistant.assistant.shared.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.localassistant.assistant.shared.mcp.infrastructure.config.AssistantMcpProperties;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@Component
@Profile("!test & !ingest-rag")
@Slf4j
final class StdioMcpToolInvoker implements McpToolInvoker, DisposableBean {

    private final Map<String, McpSyncClient> clientsByToolName;

    // Two constructors exist; the (Map) one is test-only. @Autowired tells Spring which to inject.
    @Autowired
    StdioMcpToolInvoker(final AssistantMcpProperties assistantMcpProperties, final ObjectMapper objectMapper) {
        this(Map.of(
            assistantMcpProperties.countries().toolName(),
            createClient(assistantMcpProperties.countries(), objectMapper),
            assistantMcpProperties.weather().toolName(),
            createClient(assistantMcpProperties.weather(), objectMapper)
        ));
    }

    StdioMcpToolInvoker(final Map<String, McpSyncClient> clientsByToolName) {
        this.clientsByToolName = Map.copyOf(clientsByToolName);
    }

    @Override
    public McpToolResponse invoke(final String toolName, final Map<String, Object> arguments) {
        final var client = clientsByToolName.get(toolName);
        if (client == null) {
            throw new McpToolInvocationException("no MCP client configured for tool " + toolName);
        }

        try {
            final var result = client.callTool(
                new McpSchema.CallToolRequest(toolName, arguments)
            );
            final var textContents = result.content().stream()
                .filter(McpSchema.TextContent.class::isInstance)
                .map(McpSchema.TextContent.class::cast)
                .map(McpSchema.TextContent::text)
                .toList();
            // MCP spec: an absent isError field means the call was not an error.
            final var isError = Boolean.TRUE.equals(result.isError());
            return new McpToolResponse(textContents, isError);
        } catch (RuntimeException exception) {
            log.warn("MCP tool call failed for {}", toolName, exception);
            throw new McpToolInvocationException("MCP tool call failed for " + toolName, exception);
        }
    }

    @Override
    public void destroy() {
        for (final McpSyncClient client : distinctClients()) {
            try {
                client.closeGracefully();
            } catch (RuntimeException exception) {
                log.warn("Failed to close MCP client gracefully on shutdown", exception);
            }
        }
    }

    private Set<McpSyncClient> distinctClients() {
        final Set<McpSyncClient> distinct = Collections.newSetFromMap(new IdentityHashMap<>());
        distinct.addAll(clientsByToolName.values());
        return distinct;
    }

    private static McpSyncClient createClient(final AssistantMcpProperties.McpServer server, final ObjectMapper objectMapper) {
        if (!"stdio".equalsIgnoreCase(server.transport())) {
            throw new McpToolInvocationException("unsupported MCP transport: " + server.transport());
        }
        if (StringUtils.isBlank(server.command())) {
            throw new McpToolInvocationException("MCP server command is not configured");
        }

        final var parameters = ServerParameters.builder(server.command())
            .args(server.args().toArray(String[]::new))
            .env(resolveProcessEnv(server, System::getenv))
            .build();
        final var mcpJsonMapper = new JacksonMcpJsonMapper(objectMapper);
        final var transport = new StdioClientTransport(parameters, mcpJsonMapper);
        final var client = McpClient.sync(transport)
            .requestTimeout(Duration.ofSeconds(server.timeoutSeconds()))
            .initializationTimeout(Duration.ofSeconds(server.timeoutSeconds()))
            .build();
        client.initialize();
        return client;
    }

    static Map<String, String> resolveProcessEnv(final AssistantMcpProperties.McpServer server, final Function<String, String> hostEnv) {
        final var env = new LinkedHashMap<>(server.env());
        for (final String key : server.envPassthrough()) {
            final var configured = env.get(key);
            if (StringUtils.isNotBlank(configured)) {
                continue;
            }
            final var hostValue = hostEnv.apply(key);
            if (StringUtils.isNotBlank(hostValue)) {
                env.put(key, hostValue);
            }
        }
        return Map.copyOf(env);
    }
}
