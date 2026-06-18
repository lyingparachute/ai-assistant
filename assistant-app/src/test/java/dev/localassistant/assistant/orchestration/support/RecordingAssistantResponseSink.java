package dev.localassistant.assistant.orchestration.support;

import dev.localassistant.assistant.orchestration.AssistantResponseSink;
import dev.localassistant.assistant.orchestration.SourceType;
import dev.localassistant.assistant.question.ConversationTurn;
import dev.localassistant.assistant.question.SourceContributionStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class RecordingAssistantResponseSink implements AssistantResponseSink {

    private final List<TraceEvent> traceEvents = new ArrayList<>();
    private final List<String> answerTokens = new ArrayList<>();
    private final List<String> orderedEvents = new ArrayList<>();
    private ConversationTurn completedTurn;
    private UnexpectedFailure unexpectedFailure;

    @Override
    public void recordSourceOutcome(SourceType type, SourceContributionStatus status) {
        traceEvents.add(new TraceEvent(type, status));
        orderedEvents.add("trace:" + type + ":" + status);
    }

    @Override
    public void appendAnswerToken(String delta) {
        answerTokens.add(delta);
        orderedEvents.add("token");
    }

    @Override
    public void complete(ConversationTurn turn) {
        completedTurn = turn;
        orderedEvents.add("complete");
    }

    @Override
    public void failUnexpected(String errorCode, String message) {
        unexpectedFailure = new UnexpectedFailure(errorCode, message);
    }

    public List<TraceEvent> traceEvents() {
        return List.copyOf(traceEvents);
    }

    public List<String> answerTokens() {
        return List.copyOf(answerTokens);
    }

    public ConversationTurn completedTurn() {
        return completedTurn;
    }

    public List<String> orderedEvents() {
        return List.copyOf(orderedEvents);
    }

    public Optional<UnexpectedFailure> unexpectedFailure() {
        return Optional.ofNullable(unexpectedFailure);
    }

    public record TraceEvent(SourceType type, SourceContributionStatus status) {}

    public record UnexpectedFailure(String errorCode, String message) {}
}
