package dev.localassistant.assistant.adapters.outbound.mcp;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StdioMcpToolInvokerResultMappingTest {

    private static final String WEATHER_TOOL = "get-weather";
    private static final String WEATHER_TEXT = "the weather in Munich is currently: 23.1";

    @Test
    void mapsAbsentIsErrorAsSuccess() {
        StdioMcpToolInvoker invoker = invokerReturning(callToolResult(WEATHER_TEXT, null));

        McpToolInvoker.McpToolResponse response = invoker.invoke(WEATHER_TOOL, Map.of("city", "Munich"));

        assertThat(response.isError()).isFalse();
        assertThat(response.textContents()).containsExactly(WEATHER_TEXT);
    }

    @Test
    void mapsExplicitIsErrorFalseAsSuccess() {
        StdioMcpToolInvoker invoker = invokerReturning(callToolResult(WEATHER_TEXT, false));

        McpToolInvoker.McpToolResponse response = invoker.invoke(WEATHER_TOOL, Map.of("city", "Munich"));

        assertThat(response.isError()).isFalse();
        assertThat(response.textContents()).containsExactly(WEATHER_TEXT);
    }

    @Test
    void mapsExplicitIsErrorTrueAsError() {
        StdioMcpToolInvoker invoker = invokerReturning(callToolResult("upstream failure", true));

        McpToolInvoker.McpToolResponse response = invoker.invoke(WEATHER_TOOL, Map.of("city", "Munich"));

        assertThat(response.isError()).isTrue();
        assertThat(response.textContents()).containsExactly("upstream failure");
    }

    private static StdioMcpToolInvoker invokerReturning(McpSchema.CallToolResult result) {
        McpSyncClient client = mock(McpSyncClient.class);
        when(client.callTool(any(McpSchema.CallToolRequest.class))).thenReturn(result);
        return new StdioMcpToolInvoker(Map.of(WEATHER_TOOL, client));
    }

    private static McpSchema.CallToolResult callToolResult(String text, Boolean isError) {
        return new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent(text)),
                isError,
                null,
                null
        );
    }
}
