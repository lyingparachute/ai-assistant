package dev.localassistant.assistant.answering.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record AssistantAnswer(
    String answerText, List<AnswerSource> sources, String storedTraceCorrelationId) {

    private static final String ABSENT_TRACE_CORRELATION_ID = "";

    public AssistantAnswer {
        Objects.requireNonNull(answerText, "answerText");
        Objects.requireNonNull(sources, "sources");
        Objects.requireNonNull(storedTraceCorrelationId, "storedTraceCorrelationId");
        if (answerText.isBlank()) {
            throw new IllegalArgumentException("answerText must not be blank");
        }
        sources = List.copyOf(sources);
    }

    public static AssistantAnswer of(final String answerText, final List<AnswerSource> sources) {
        return new AssistantAnswer(answerText, sources, ABSENT_TRACE_CORRELATION_ID);
    }

    public static AssistantAnswer withTrace(
        final String answerText, final List<AnswerSource> sources, final String traceCorrelationId) {
        Objects.requireNonNull(traceCorrelationId, "traceCorrelationId");
        if (traceCorrelationId.isBlank()) {
            throw new IllegalArgumentException("traceCorrelationId must not be blank");
        }
        return new AssistantAnswer(answerText, sources, traceCorrelationId);
    }

    public Optional<String> traceCorrelationId() {
        return storedTraceCorrelationId.isBlank()
            ? Optional.empty()
            : Optional.of(storedTraceCorrelationId);
    }
}
