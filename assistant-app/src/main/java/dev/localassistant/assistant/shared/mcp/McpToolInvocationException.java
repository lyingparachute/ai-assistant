package dev.localassistant.assistant.shared.mcp;

public final class McpToolInvocationException extends RuntimeException {

    public McpToolInvocationException(final String message) {
        super(message);
    }

    public McpToolInvocationException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
