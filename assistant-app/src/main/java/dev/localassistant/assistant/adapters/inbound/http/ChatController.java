package dev.localassistant.assistant.adapters.inbound.http;

import dev.localassistant.assistant.config.AssistantChatProperties;
import dev.localassistant.assistant.orchestration.AnswerQuestionUseCase;
import dev.localassistant.assistant.question.UserQuestion;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
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
public class ChatController {

    private static final String INTERNAL_ERROR_CODE = "internal_error";
    private static final String INTERNAL_ERROR_MESSAGE = "an unexpected error occurred";
    private static final String STREAM_TIMEOUT_CODE = "stream_timeout";
    private static final String STREAM_TIMEOUT_MESSAGE = "chat stream timed out";

    private final AnswerQuestionUseCase answerQuestionUseCase;
    private final ChatHttpMapper chatHttpMapper;
    private final AssistantChatProperties chatProperties;
    private final Executor chatStreamExecutor;

    public ChatController(
            AnswerQuestionUseCase answerQuestionUseCase,
            ChatHttpMapper chatHttpMapper,
            AssistantChatProperties chatProperties,
            @Qualifier("chatStreamExecutor") Executor chatStreamExecutor) {
        this.answerQuestionUseCase = answerQuestionUseCase;
        this.chatHttpMapper = chatHttpMapper;
        this.chatProperties = chatProperties;
        this.chatStreamExecutor = chatStreamExecutor;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter chat(@Valid @RequestBody ChatRequest request) {
        long timeoutMillis = chatProperties.streamTimeoutSeconds() * 1000L;
        SseEmitter emitter = new SseEmitter(timeoutMillis);
        UserQuestion question = chatHttpMapper.toUserQuestion(request);
        SseAssistantResponseSink sink = new SseAssistantResponseSink(emitter, chatHttpMapper);
        ChatStreamTask work = new ChatStreamTask(answerQuestionUseCase, question, sink);
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
