package dev.localassistant.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SseChatStreamParserTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void parseFinalChatResponseMatchesCapturedPreMigrationJsonShape() throws Exception {
        String sseBody = readFixture("fixtures/germany-capital-sse-stream.txt");
        JsonNode expected = readJsonFixture("fixtures/germany-capital-chat-response.json");

        JsonNode parsedFinal = SseChatStreamParser.parseFinalChatResponse(sseBody);

        ChatResponseSemanticEquality.assertSemanticallyEqual(parsedFinal, expected);
    }

    @Test
    void parseFinalChatResponseFailsWhenStreamEndsWithErrorEvent() {
        String sseBody =
                """
                event:error
                data:{"error":"internal_error","message":"an unexpected error occurred"}

                """;

        assertThatThrownBy(() -> SseChatStreamParser.parseFinalChatResponse(sseBody))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("internal_error");
    }

    @Test
    void parseEventsPreservesEventOrder() throws IOException {
        String sseBody = readFixture("fixtures/germany-capital-sse-stream.txt");

        assertThat(SseChatStreamParser.parseEvents(sseBody))
                .extracting(SseChatStreamParser.SseEvent::name)
                .containsExactly("trace", "final");
    }

    private static String readFixture(String resourcePath) throws IOException {
        try (InputStream inputStream =
                SseChatStreamParserTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalStateException("Missing test resource: " + resourcePath);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static JsonNode readJsonFixture(String resourcePath) throws IOException {
        try (InputStream inputStream =
                SseChatStreamParserTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalStateException("Missing test resource: " + resourcePath);
            }
            return OBJECT_MAPPER.readTree(inputStream);
        }
    }
}
