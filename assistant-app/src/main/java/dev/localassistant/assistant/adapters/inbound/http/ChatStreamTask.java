package dev.localassistant.assistant.adapters.inbound.http;

import dev.localassistant.assistant.orchestration.AnswerQuestionUseCase;
import dev.localassistant.assistant.question.UserQuestion;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

final class ChatStreamTask implements Runnable {

    private static final String INTERNAL_ERROR_CODE = "internal_error";
    private static final String INTERNAL_ERROR_MESSAGE = "an unexpected error occurred";

    private final AnswerQuestionUseCase answerQuestionUseCase;
    private final UserQuestion question;
    private final SseAssistantResponseSink sink;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicReference<Thread> workerThread = new AtomicReference<>();

    ChatStreamTask(
            AnswerQuestionUseCase answerQuestionUseCase,
            UserQuestion question,
            SseAssistantResponseSink sink) {
        this.answerQuestionUseCase = answerQuestionUseCase;
        this.question = question;
        this.sink = sink;
    }

    @Override
    public void run() {
        if (cancelled.get()) {
            return;
        }
        Thread currentThread = Thread.currentThread();
        workerThread.set(currentThread);
        try {
            if (!cancelled.get()) {
                answerQuestionUseCase.answer(question, sink);
            }
        } catch (RuntimeException exception) {
            if (!cancelled.get()) {
                sink.failUnexpected(INTERNAL_ERROR_CODE, INTERNAL_ERROR_MESSAGE);
            }
        } finally {
            workerThread.compareAndSet(currentThread, null);
            if (cancelled.get()) {
                Thread.interrupted();
            }
        }
    }

    void cancel() {
        cancelled.set(true);
        Thread worker = workerThread.get();
        if (worker != null) {
            worker.interrupt();
        }
    }
}
