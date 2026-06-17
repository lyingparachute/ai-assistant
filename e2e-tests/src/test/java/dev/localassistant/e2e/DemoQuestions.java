package dev.localassistant.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;

final class DemoQuestions {

    private static final String RESOURCE = "/demo-questions.json";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Map<String, String> questionByKey;

    private DemoQuestions(Map<String, String> questionByKey) {
        this.questionByKey = Map.copyOf(questionByKey);
    }

    static DemoQuestions load() {
        try (InputStream stream = DemoQuestions.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Demo question resource not found on classpath: " + RESOURCE);
            }
            JsonNode root = OBJECT_MAPPER.readTree(stream);
            Map<String, String> byKey = new LinkedHashMap<>();
            for (JsonNode entry : root.path("questions")) {
                String key = entry.path("key").asText();
                String question = entry.path("question").asText();
                if (key.isBlank() || question.isBlank()) {
                    throw new IllegalStateException("Demo question entry missing key or question: " + entry);
                }
                byKey.put(key, question);
            }
            if (byKey.isEmpty()) {
                throw new IllegalStateException("No demo questions defined in " + RESOURCE);
            }
            return new DemoQuestions(byKey);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read demo question resource " + RESOURCE, exception);
        }
    }

    String question(String key) {
        String question = questionByKey.get(key);
        if (question == null) {
            throw new IllegalArgumentException("No demo question registered for key '" + key + "'");
        }
        return question;
    }
}
