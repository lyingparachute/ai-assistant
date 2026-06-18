package dev.localassistant.assistant.shared.mcp;

public final class McpToolInvocationException extends RuntimeException {

    public McpToolInvocationException(String message) {
        super(message);
    }

    public McpToolInvocationException(String message, Throwable cause) {
        super(message, cause);
    }
}
