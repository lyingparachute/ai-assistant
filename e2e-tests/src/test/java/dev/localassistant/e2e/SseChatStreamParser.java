package dev.localassistant.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class SseChatStreamParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private SseChatStreamParser() {}

    static JsonNode parseFinalChatResponse(String sseBody) throws IOException {
        List<SseEvent> events = parseEvents(sseBody);
        JsonNode errorPayload = null;
        for (SseEvent event : events) {
            if ("error".equals(event.name())) {
                errorPayload = OBJECT_MAPPER.readTree(event.data());
            }
            if ("final".equals(event.name())) {
                return OBJECT_MAPPER.readTree(event.data());
            }
        }
        if (errorPayload != null) {
            throw new IllegalStateException(
                    "SSE stream ended with error event: "
                            + errorPayload.path("error").asText()
                            + " — "
                            + errorPayload.path("message").asText());
        }
        throw new IllegalStateException(
                "SSE stream did not contain a final event; events="
                        + events.stream().map(SseEvent::name).toList());
    }

    static List<SseEvent> parseEvents(String content) {
        List<SseEvent> events = new ArrayList<>();
        String currentName = null;
        StringBuilder data = new StringBuilder();
        for (String line : content.split("\n", -1)) {
            if (line.startsWith("event:")) {
                currentName = line.substring("event:".length()).trim();
            } else if (line.startsWith("data:")) {
                if (!data.isEmpty()) {
                    data.append('\n');
                }
                data.append(line.substring("data:".length()).trim());
            } else if (line.isEmpty() && currentName != null) {
                events.add(new SseEvent(currentName, data.toString()));
                currentName = null;
                data = new StringBuilder();
            }
        }
        if (currentName != null) {
            events.add(new SseEvent(currentName, data.toString()));
        }
        return events;
    }

    record SseEvent(String name, String data) {}
}
