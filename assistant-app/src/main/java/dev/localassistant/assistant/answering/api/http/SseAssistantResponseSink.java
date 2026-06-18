package dev.localassistant.assistant.answering.api.http;

import dev.localassistant.assistant.answering.domain.AssistantResponseSink;
import dev.localassistant.assistant.answering.domain.ConversationTurn;
import dev.localassistant.assistant.answering.domain.SourceContributionStatus;
import dev.localassistant.assistant.answering.domain.SourceType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

final class SseAssistantResponseSink implements AssistantResponseSink {

    private final SseEmitter emitter;
    private final ChatHttpMapper chatHttpMapper;
    private final AtomicBoolean terminalEventSent = new AtomicBoolean(false);

    SseAssistantResponseSink(final SseEmitter emitter, final ChatHttpMapper chatHttpMapper) {
        this.emitter = emitter;
        this.chatHttpMapper = chatHttpMapper;
    }

    @Override
    public void recordSourceOutcome(final SourceType type, final SourceContributionStatus status) {
        if (terminalEventSent.get()) {
            return;
        }
        sendEvent("trace", new TraceEventPayload(toTraceType(type), status.name()));
    }

    @Override
    public void appendAnswerToken(final String delta) {
        if (terminalEventSent.get()) {
            return;
        }
        sendEvent("token", new TokenEventPayload(delta));
    }

    @Override
    public void complete(final ConversationTurn turn) {
        if (!terminalEventSent.compareAndSet(false, true)) {
            return;
        }
        sendTerminalEvent("final", chatHttpMapper.toChatResponse(turn));
    }

    @Override
    public void failUnexpected(final String errorCode, final String message) {
        if (!terminalEventSent.compareAndSet(false, true)) {
            return;
        }
        sendTerminalEvent("error", new ApiErrorResponse(errorCode, message));
    }

    boolean terminalEventSent() {
        return terminalEventSent.get();
    }

    private void sendEvent(final String name, final Object payload) {
        try {
            emitter.send(SseEmitter.event().name(name).data(payload));
        } catch (IOException exception) {
            terminalEventSent.set(true);
            emitter.completeWithError(exception);
        }
    }

    private void sendTerminalEvent(final String name, final Object payload) {
        try {
            emitter.send(SseEmitter.event().name(name).data(payload));
            emitter.complete();
        } catch (IOException exception) {
            emitter.completeWithError(exception);
        }
    }

    private static String toTraceType(final SourceType type) {
        return switch (type) {
            case COUNTRIES_FACTS -> "countries_facts";
            case WEATHER_OBSERVATION -> "weather_observation";
            case RAG_KNOWLEDGE -> "rag_knowledge";
            case MODEL_SYNTHESIS -> "model_synthesis";
        };
    }

    record TraceEventPayload(String type, String status) {
    }

    record TokenEventPayload(String text) {
    }
}
