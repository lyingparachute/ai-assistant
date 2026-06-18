package dev.localassistant.e2e;

import com.fasterxml.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

final class ChatResponseSemanticEquality {

    private ChatResponseSemanticEquality() {}

    static void assertSemanticallyEqual(JsonNode actual, JsonNode expected) {
        assertThat(actual.path("answerText").asText()).isEqualTo(expected.path("answerText").asText());
        assertSourcesSemanticallyEqual(actual.path("sources"), expected.path("sources"));
        assertTraceCorrelationIdSemanticallyEqual(actual, expected);
    }

    private static void assertSourcesSemanticallyEqual(JsonNode actualSources, JsonNode expectedSources) {
        assertThat(actualSources.isArray()).isTrue();
        assertThat(expectedSources.isArray()).isTrue();
        assertThat(actualSources.size()).isEqualTo(expectedSources.size());
        for (int index = 0; index < expectedSources.size(); index++) {
            assertSourceSemanticallyEqual(actualSources.get(index), expectedSources.get(index));
        }
    }

    private static void assertSourceSemanticallyEqual(JsonNode actual, JsonNode expected) {
        assertThat(actual.path("type").asText()).isEqualTo(expected.path("type").asText());
        assertThat(actual.path("status").asText()).isEqualTo(expected.path("status").asText());
        expected.fieldNames()
                .forEachRemaining(
                        fieldName -> {
                            if ("type".equals(fieldName) || "status".equals(fieldName)) {
                                return;
                            }
                            assertThat(actual.has(fieldName))
                                    .as("source field '%s'", fieldName)
                                    .isTrue();
                            assertThat(actual.get(fieldName)).isEqualTo(expected.get(fieldName));
                        });
    }

    private static void assertTraceCorrelationIdSemanticallyEqual(JsonNode actual, JsonNode expected) {
        if (expected.has("traceCorrelationId")) {
            assertThat(actual.has("traceCorrelationId")).isTrue();
            assertThat(actual.path("traceCorrelationId").asText()).isNotBlank();
            return;
        }
        assertThat(actual.has("traceCorrelationId")).isFalse();
    }
}
