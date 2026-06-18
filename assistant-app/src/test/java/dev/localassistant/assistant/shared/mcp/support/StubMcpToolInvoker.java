package dev.localassistant.assistant.shared.mcp.support;

import dev.localassistant.assistant.shared.mcp.McpToolInvocationException;
import dev.localassistant.assistant.shared.mcp.McpToolInvoker;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class StubMcpToolInvoker implements McpToolInvoker {

    private final Map<String, Function<Map<String, Object>, McpToolResponse>> handlers = new ConcurrentHashMap<>();
    private final Map<String, RuntimeException> failures = new ConcurrentHashMap<>();

    public StubMcpToolInvoker reset() {
        handlers.clear();
        failures.clear();
        return this;
    }

    public StubMcpToolInvoker when(String toolName, Function<Map<String, Object>, McpToolResponse> handler) {
        handlers.put(toolName, handler);
        return this;
    }

    public StubMcpToolInvoker fail(String toolName, RuntimeException exception) {
        failures.put(toolName, exception);
        return this;
    }

    @Override
    public McpToolResponse invoke(String toolName, Map<String, Object> arguments) {
        RuntimeException failure = failures.get(toolName);
        if (failure != null) {
            throw failure;
        }

        Function<Map<String, Object>, McpToolResponse> handler = handlers.get(toolName);
        if (handler == null) {
            throw new McpToolInvocationException("no stub handler for tool " + toolName);
        }
        return handler.apply(arguments);
    }

    public static McpToolResponse textResponse(String text) {
        return new McpToolResponse(List.of(text), false);
    }

    public static McpToolResponse textError(String text) {
        return new McpToolResponse(List.of(text), true);
    }
}
