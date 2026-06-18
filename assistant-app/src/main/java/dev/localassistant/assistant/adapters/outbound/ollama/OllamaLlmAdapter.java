package dev.localassistant.assistant.adapters.outbound.ollama;

import dev.localassistant.assistant.llm.LlmPort;
import dev.localassistant.assistant.llm.LlmResult;
import dev.localassistant.assistant.llm.PromptContext;
import dev.localassistant.assistant.llm.TokenSink;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.Disposable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class OllamaLlmAdapter implements LlmPort {

    static final String SOURCE_LABEL = "Ollama chat";
    static final String UNAVAILABLE_HINT =
            "Ensure Ollama is running and the configured chat model is pulled. "
                    + "Retry later; do not invent missing facts.";
    static final String EMPTY_COMPLETION_MESSAGE = "Ollama returned an empty completion";
    static final String REQUEST_FAILED_MESSAGE = "Ollama chat request failed";
    static final String STREAM_TIMEOUT_MESSAGE = "Ollama chat stream timed out";
    private static final String GROUNDED_FACTS_HEADER = "Grounded facts:";
    private static final String FACT_BULLET_PREFIX = "- ";
    private static final String USER_QUESTION_PREFIX = "User question: ";
    private static final long LATCH_AWAIT_GRACE_SECONDS = 5;

    private final ChatModel chatModel;
    private final Duration streamTimeout;

    public OllamaLlmAdapter(ChatModel chatModel, Duration streamTimeout) {
        this.chatModel = chatModel;
        this.streamTimeout = streamTimeout;
    }

    @Override
    public LlmResult generate(PromptContext context, TokenSink tokenSink) {
        Disposable subscription = null;
        try {
            Prompt prompt = new Prompt(List.of(new UserMessage(buildPromptText(context))));
            CountDownLatch completionLatch = new CountDownLatch(1);
            AtomicReference<Throwable> streamFailure = new AtomicReference<>();
            StringBuilder assembledText = new StringBuilder();
            subscription =
                    chatModel
                            .stream(prompt)
                            .timeout(streamTimeout)
                            .subscribe(
                                    chunk -> emitDelta(chunk, tokenSink, assembledText),
                                    error -> {
                                        streamFailure.set(error);
                                        completionLatch.countDown();
                                    },
                                    completionLatch::countDown);
            boolean completed = awaitStreamCompletion(completionLatch, subscription);
            if (!completed) {
                return new LlmResult.SourceUnavailable(
                        SOURCE_LABEL, STREAM_TIMEOUT_MESSAGE, UNAVAILABLE_HINT);
            }
            if (streamFailure.get() != null) {
                return sourceUnavailable(streamFailure.get());
            }
            String finalText = assembledText.toString();
            if (StringUtils.isBlank(finalText)) {
                return new LlmResult.SourceUnavailable(
                        SOURCE_LABEL, EMPTY_COMPLETION_MESSAGE, UNAVAILABLE_HINT);
            }
            return new LlmResult.Success(finalText);
        } catch (InterruptedException interrupted) {
            if (subscription != null) {
                subscription.dispose();
            }
            Thread.currentThread().interrupt();
            return sourceUnavailable(interrupted);
        } catch (Exception exception) {
            return sourceUnavailable(exception);
        }
    }

    static String buildPromptText(PromptContext context) {
        List<String> sections = new ArrayList<>();
        sections.add(context.synthesisInstructions());
        if (!context.groundedFacts().isEmpty()) {
            sections.add(GROUNDED_FACTS_HEADER);
            for (String fact : context.groundedFacts()) {
                sections.add(FACT_BULLET_PREFIX + fact);
            }
        }
        sections.add(USER_QUESTION_PREFIX + context.userQuestionText());
        return String.join(System.lineSeparator(), sections);
    }

    private boolean awaitStreamCompletion(CountDownLatch completionLatch, Disposable subscription)
            throws InterruptedException {
        long awaitMillis = streamTimeout.toMillis() + Duration.ofSeconds(LATCH_AWAIT_GRACE_SECONDS).toMillis();
        if (!completionLatch.await(awaitMillis, TimeUnit.MILLISECONDS)) {
            subscription.dispose();
            completionLatch.countDown();
            return false;
        }
        return true;
    }

    private static void emitDelta(ChatResponse chunk, TokenSink tokenSink, StringBuilder assembledText) {
        String delta = extractAssistantText(chunk);
        if (StringUtils.isNotBlank(delta)) {
            assembledText.append(delta);
            tokenSink.accept(delta);
        }
    }

    private static LlmResult.SourceUnavailable sourceUnavailable(Throwable failure) {
        return new LlmResult.SourceUnavailable(
                SOURCE_LABEL,
                StringUtils.defaultIfBlank(failure.getMessage(), REQUEST_FAILED_MESSAGE),
                UNAVAILABLE_HINT);
    }

    private static String extractAssistantText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        final var text = response.getResult().getOutput().getText();
        return text == null ? "" : text;
    }
}
