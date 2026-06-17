package dev.localassistant.assistant.question;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record AssistantAnswer(
        String answerText,
        List<AnswerSource> sources,
        String storedTraceCorrelationId,
        boolean hasTraceCorrelationId) {

    public AssistantAnswer {
        Objects.requireNonNull(answerText, "answerText");
        Objects.requireNonNull(sources, "sources");
        Objects.requireNonNull(storedTraceCorrelationId, "storedTraceCorrelationId");
        if (answerText.isBlank()) {
            throw new IllegalArgumentException("answerText must not be blank");
        }
        sources = List.copyOf(sources);
        if (hasTraceCorrelationId && storedTraceCorrelationId.isBlank()) {
            throw new IllegalArgumentException("storedTraceCorrelationId must not be blank when present");
        }
        if (!hasTraceCorrelationId && !storedTraceCorrelationId.isBlank()) {
            throw new IllegalArgumentException("storedTraceCorrelationId requires hasTraceCorrelationId");
        }
    }

    public static AssistantAnswer of(String answerText, List<AnswerSource> sources) {
        return new AssistantAnswer(answerText, sources, "", false);
    }

    public static AssistantAnswer withTrace(
            String answerText, List<AnswerSource> sources, String traceCorrelationId) {
        Objects.requireNonNull(traceCorrelationId, "traceCorrelationId");
        if (traceCorrelationId.isBlank()) {
            throw new IllegalArgumentException("traceCorrelationId must not be blank");
        }
        return new AssistantAnswer(answerText, sources, traceCorrelationId, true);
    }

    public Optional<String> traceCorrelationId() {
        return hasTraceCorrelationId ? Optional.of(storedTraceCorrelationId) : Optional.empty();
    }
}
