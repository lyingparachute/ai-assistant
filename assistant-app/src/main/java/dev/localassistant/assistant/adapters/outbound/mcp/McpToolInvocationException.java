package dev.localassistant.assistant.adapters.outbound.mcp;

public final class McpToolInvocationException extends RuntimeException {

    public McpToolInvocationException(String message) {
        super(message);
    }

    public McpToolInvocationException(String message, Throwable cause) {
        super(message, cause);
    }
}
