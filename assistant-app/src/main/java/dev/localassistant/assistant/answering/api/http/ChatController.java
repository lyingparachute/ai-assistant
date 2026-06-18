package dev.localassistant.assistant.answering.api.http;

import dev.localassistant.assistant.answering.domain.port.inbound.AnswerQuestion;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@RestController
@RequestMapping("/api")
@Profile("!ingest-rag")
@RequiredArgsConstructor
public final class ChatController {

    private static final String INTERNAL_ERROR_CODE = "internal_error";
    private static final String INTERNAL_ERROR_MESSAGE = "an unexpected error occurred";
    private static final String STREAM_TIMEOUT_CODE = "stream_timeout";
    private static final String STREAM_TIMEOUT_MESSAGE = "chat stream timed out";

    private final AnswerQuestion answerQuestion;
    private final ChatHttpMapper chatHttpMapper;
    private final AssistantChatProperties chatProperties;
    @Qualifier("chatStreamExecutor")
    private final Executor chatStreamExecutor;

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter chat(@Valid @RequestBody final ChatRequest request) {
        final var timeoutMillis = chatProperties.streamTimeoutSeconds() * 1000L;
        final var emitter = new SseEmitter(timeoutMillis);
        final var question = chatHttpMapper.toUserQuestion(request);
        final var sink = new SseAssistantResponseSink(emitter, chatHttpMapper);
        final var work = new ChatStreamTask(answerQuestion, question, sink);
        emitter.onTimeout(() -> {
            work.cancel();
            sink.failUnexpected(STREAM_TIMEOUT_CODE, STREAM_TIMEOUT_MESSAGE);
        });
        emitter.onError(error -> {
            work.cancel();
            sink.failUnexpected(INTERNAL_ERROR_CODE, INTERNAL_ERROR_MESSAGE);
        });
        emitter.onCompletion(() -> {
            if (!sink.terminalEventSent()) {
                work.cancel();
            }
        });

        try {
            chatStreamExecutor.execute(work);
        } catch (RejectedExecutionException exception) {
            work.cancel();
            sink.failUnexpected(INTERNAL_ERROR_CODE, INTERNAL_ERROR_MESSAGE);
        }

        return emitter;
    }
}
