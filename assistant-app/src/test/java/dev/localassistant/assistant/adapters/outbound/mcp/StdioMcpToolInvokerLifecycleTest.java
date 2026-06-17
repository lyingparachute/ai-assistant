package dev.localassistant.assistant.adapters.outbound.mcp;

import io.modelcontextprotocol.client.McpSyncClient;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class StdioMcpToolInvokerLifecycleTest {

    @Test
    void destroyClosesEveryDistinctClientGracefully() {
        McpSyncClient countriesClient = mock(McpSyncClient.class);
        McpSyncClient weatherClient = mock(McpSyncClient.class);
        StdioMcpToolInvoker invoker = new StdioMcpToolInvoker(Map.of(
                "country_lookup", countriesClient,
                "get-weather", weatherClient
        ));

        invoker.destroy();

        verify(countriesClient, times(1)).closeGracefully();
        verify(weatherClient, times(1)).closeGracefully();
    }

    @Test
    void destroyClosesSharedClientOnlyOnce() {
        McpSyncClient sharedClient = mock(McpSyncClient.class);
        Map<String, McpSyncClient> clientsByToolName = new LinkedHashMap<>();
        clientsByToolName.put("country_lookup", sharedClient);
        clientsByToolName.put("get-weather", sharedClient);
        StdioMcpToolInvoker invoker = new StdioMcpToolInvoker(clientsByToolName);

        invoker.destroy();

        verify(sharedClient, times(1)).closeGracefully();
    }
}
