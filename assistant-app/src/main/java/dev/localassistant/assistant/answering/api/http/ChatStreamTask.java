package dev.localassistant.assistant.answering.api.http;

import dev.localassistant.assistant.answering.domain.UserQuestion;
import dev.localassistant.assistant.answering.domain.port.inbound.AnswerQuestion;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

final class ChatStreamTask implements Runnable {

    private static final String INTERNAL_ERROR_CODE = "internal_error";
    private static final String INTERNAL_ERROR_MESSAGE = "an unexpected error occurred";

    private final AnswerQuestion answerQuestion;
    private final UserQuestion question;
    private final SseAssistantResponseSink sink;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicReference<Thread> workerThread = new AtomicReference<>();

    ChatStreamTask(
        final AnswerQuestion answerQuestion,
        final UserQuestion question,
        final SseAssistantResponseSink sink) {
        this.answerQuestion = answerQuestion;
        this.question = question;
        this.sink = sink;
    }

    @Override
    public void run() {
        if (cancelled.get()) {
            return;
        }
        final var currentThread = Thread.currentThread();
        workerThread.set(currentThread);
        try {
            if (!cancelled.get()) {
                answerQuestion.execute(new AnswerQuestion.Command(question, sink));
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
        final var worker = workerThread.get();
        if (worker != null) {
            worker.interrupt();
        }
    }
}
