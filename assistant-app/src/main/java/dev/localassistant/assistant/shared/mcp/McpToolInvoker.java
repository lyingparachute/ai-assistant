package dev.localassistant.assistant.shared.mcp;

import java.util.List;
import java.util.Map;

public interface McpToolInvoker {

    McpToolResponse invoke(String toolName, Map<String, Object> arguments);

    record McpToolResponse(List<String> textContents, boolean isError) {
        public McpToolResponse {
            textContents = List.copyOf(textContents);
        }

        public String firstTextContent() {
            return textContents.isEmpty() ? "" : textContents.getFirst();
        }
    }
}
