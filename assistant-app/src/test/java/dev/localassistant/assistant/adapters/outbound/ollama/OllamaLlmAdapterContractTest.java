package dev.localassistant.assistant.adapters.outbound.ollama;

import dev.localassistant.assistant.llm.LlmResult;
import dev.localassistant.assistant.llm.PromptContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OllamaLlmAdapterContractTest {

    @Mock
    private ChatModel chatModel;

    private OllamaLlmAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new OllamaLlmAdapter(chatModel);
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
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("Fraud Guard helps detect fraud."));

        adapter.generate(context);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        UserMessage userMessage = (UserMessage) promptCaptor.getValue().getInstructions().getFirst();
        assertThat(userMessage.getText()).isEqualTo(OllamaLlmAdapter.buildPromptText(context));
    }

    @Test
    void returnsSuccessWhenChatModelReturnsText() {
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("Berlin is Germany's capital."));

        LlmResult result =
                adapter.generate(
                        new PromptContext(
                                "What do you know about Berlin?",
                                List.of("Berlin is the capital of Germany."),
                                "Answer using only grounded facts."));

        assertThat(result).isInstanceOf(LlmResult.Success.class);
        assertThat(((LlmResult.Success) result).text()).isEqualTo("Berlin is Germany's capital.");
    }

    @Test
    void returnsSourceUnavailableWhenChatModelThrows() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("connection refused"));

        LlmResult result =
                adapter.generate(
                        new PromptContext(
                                "What do you know about Berlin?",
                                List.of(),
                                "Answer using only grounded facts."));

        assertThat(result).isInstanceOf(LlmResult.SourceUnavailable.class);
        LlmResult.SourceUnavailable unavailable = (LlmResult.SourceUnavailable) result;
        assertThat(unavailable.sourceLabel()).isEqualTo(OllamaLlmAdapter.SOURCE_LABEL);
        assertThat(unavailable.message()).isEqualTo("connection refused");
        assertThat(unavailable.hint()).isEqualTo(OllamaLlmAdapter.UNAVAILABLE_HINT);
    }

    @Test
    void returnsSourceUnavailableWhenChatModelReturnsBlankText() {
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("   "));

        LlmResult result =
                adapter.generate(
                        new PromptContext(
                                "What do you know about Berlin?",
                                List.of(),
                                "Answer using only grounded facts."));

        assertThat(result).isInstanceOf(LlmResult.SourceUnavailable.class);
        LlmResult.SourceUnavailable unavailable = (LlmResult.SourceUnavailable) result;
        assertThat(unavailable.message()).contains("empty");
    }

    private static ChatResponse chatResponse(String text) {
        return new ChatResponse(List.of(new Generation(new org.springframework.ai.chat.messages.AssistantMessage(text))));
    }
}
