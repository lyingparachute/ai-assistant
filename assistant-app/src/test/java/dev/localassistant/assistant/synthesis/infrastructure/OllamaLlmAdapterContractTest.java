package dev.localassistant.assistant.synthesis.infrastructure;

import dev.localassistant.assistant.synthesis.domain.LlmResult;
import dev.localassistant.assistant.synthesis.domain.PromptContext;
import dev.localassistant.assistant.synthesis.domain.TokenSink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OllamaLlmAdapterContractTest {

    private static final Duration STREAM_TIMEOUT = Duration.ofSeconds(5);

    @Mock
    private ChatModel chatModel;

    private OllamaLlmAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new OllamaLlmAdapter(chatModel, STREAM_TIMEOUT);
    }

    @Test
    void buildPromptTextOmitsGroundedFactsSectionWhenEmpty() {
        PromptContext context =
                new PromptContext(
                        "What do you know about Berlin?",
                        List.of(),
                        "Answer using only grounded facts.");

        String promptText = OllamaLlmAdapter.buildPromptText(context);

        assertThat(promptText)
                .doesNotContain("Grounded facts:")
                .contains("Answer using only grounded facts.")
                .contains("User question: What do you know about Berlin?");
    }

    @Test
    void buildPromptTextIncludesInstructionsFactsAndQuestion() {
        PromptContext context =
                new PromptContext(
                        "What do you know about Berlin?",
                        List.of("Berlin is the capital of Germany."),
                        "Answer using only grounded facts.");

        String promptText = OllamaLlmAdapter.buildPromptText(context);

        assertThat(promptText)
                .contains("Answer using only grounded facts.")
                .contains("- Berlin is the capital of Germany.")
                .contains("User question: What do you know about Berlin?");
    }

    @Test
    void generateSendsCompactPromptToChatModel() {
        PromptContext context =
                new PromptContext(
                        "What is CDQ Fraud Guard?",
                        List.of("Fraud Guard monitors transactions."),
                        "Summarize product knowledge.");
        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.just(chatResponse("Fraud Guard helps detect fraud.")));

        adapter.generate(context, TokenSink.discarding());

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).stream(promptCaptor.capture());
        UserMessage userMessage = (UserMessage) promptCaptor.getValue().getInstructions().getFirst();
        assertThat(userMessage.getText()).isEqualTo(OllamaLlmAdapter.buildPromptText(context));
    }

    @Test
    void emittedDeltasJoinToFinalSuccessText() {
        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(
                        Flux.just(
                                chatResponse("Berlin "),
                                chatResponse("is Germany's capital.")));
        List<String> emittedDeltas = new ArrayList<>();

        LlmResult result =
                adapter.generate(
                        new PromptContext(
                                "What do you know about Berlin?",
                                List.of("Berlin is the capital of Germany."),
                                "Answer using only grounded facts."),
                        emittedDeltas::add);

        assertThat(emittedDeltas).containsExactly("Berlin ", "is Germany's capital.");
        assertThat(result).isInstanceOf(LlmResult.Success.class);
        assertThat(((LlmResult.Success) result).text()).isEqualTo("Berlin is Germany's capital.");
    }

    @Test
    void ignoresBlankStreamDeltas() {
        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(
                        Flux.just(
                                chatResponse("Hello"),
                                chatResponse("   "),
                                chatResponse(""),
                                chatResponse(" world")));
        List<String> emittedDeltas = new ArrayList<>();

        LlmResult result =
                adapter.generate(
                        new PromptContext(
                                "What do you know about Berlin?",
                                List.of(),
                                "Answer using only grounded facts."),
                        emittedDeltas::add);

        assertThat(emittedDeltas).containsExactly("Hello", " world");
        assertThat(result).isInstanceOf(LlmResult.Success.class);
        assertThat(((LlmResult.Success) result).text()).isEqualTo("Hello world");
    }

    @Test
    void returnsSourceUnavailableWhenStreamFails() {
        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.error(new RuntimeException("connection refused")));

        LlmResult result =
                adapter.generate(
                        new PromptContext(
                                "What do you know about Berlin?",
                                List.of(),
                                "Answer using only grounded facts."),
                        TokenSink.discarding());

        assertThat(result).isInstanceOf(LlmResult.SourceUnavailable.class);
        LlmResult.SourceUnavailable unavailable = (LlmResult.SourceUnavailable) result;
        assertThat(unavailable.sourceLabel()).isEqualTo(OllamaLlmAdapter.SOURCE_LABEL);
        assertThat(unavailable.message()).isEqualTo("connection refused");
        assertThat(unavailable.hint()).isEqualTo(OllamaLlmAdapter.UNAVAILABLE_HINT);
    }

    @Test
    void returnsSourceUnavailableWhenStreamCompletesWithBlankText() {
        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.just(chatResponse("   "), chatResponse("")));

        LlmResult result =
                adapter.generate(
                        new PromptContext(
                                "What do you know about Berlin?",
                                List.of(),
                                "Answer using only grounded facts."),
                        TokenSink.discarding());

        assertThat(result).isInstanceOf(LlmResult.SourceUnavailable.class);
        LlmResult.SourceUnavailable unavailable = (LlmResult.SourceUnavailable) result;
        assertThat(unavailable.message()).contains("empty");
    }

    @Test
    void interruptedGenerationDisposesActiveStreamSubscription() throws Exception {
        CountDownLatch subscribed = new CountDownLatch(1);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicReference<LlmResult> result = new AtomicReference<>();
        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(
                        Flux.<ChatResponse>never()
                                .doOnSubscribe(subscription -> subscribed.countDown())
                                .doOnCancel(() -> cancelled.set(true)));

        Thread worker =
                new Thread(
                        () -> result.set(adapter.generate(
                                new PromptContext(
                                        "What do you know about Berlin?",
                                        List.of(),
                                        "Answer using only grounded facts."),
                                TokenSink.discarding())),
                        "ollama-adapter-interrupt-test");
        worker.start();
        assertThat(subscribed.await(1, TimeUnit.SECONDS)).isTrue();

        worker.interrupt();
        worker.join(1_000);

        assertThat(worker.isAlive()).isFalse();
        assertThat(cancelled).isTrue();
        assertThat(result.get()).isInstanceOf(LlmResult.SourceUnavailable.class);
    }

    private static ChatResponse chatResponse(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
