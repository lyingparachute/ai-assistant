package dev.localassistant.assistant.adapters.outbound.ollama;

import dev.localassistant.assistant.llm.LlmPort;
import dev.localassistant.assistant.llm.LlmResult;
import dev.localassistant.assistant.llm.PromptContext;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;

public final class OllamaLlmAdapter implements LlmPort {

    static final String SOURCE_LABEL = "Ollama chat";
    static final String UNAVAILABLE_HINT =
            "Ensure Ollama is running and the configured chat model is pulled. "
                    + "Retry later; do not invent missing facts.";
    static final String EMPTY_COMPLETION_MESSAGE = "Ollama returned an empty completion";
    static final String REQUEST_FAILED_MESSAGE = "Ollama chat request failed";
    private static final String GROUNDED_FACTS_HEADER = "Grounded facts:";
    private static final String FACT_BULLET_PREFIX = "- ";
    private static final String USER_QUESTION_PREFIX = "User question: ";

    private final ChatModel chatModel;

    public OllamaLlmAdapter(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public LlmResult generate(PromptContext context) {
        try {
            Prompt prompt = new Prompt(List.of(new UserMessage(buildPromptText(context))));
            ChatResponse response = chatModel.call(prompt);
            String text = extractAssistantText(response);
            if (text.isBlank()) {
                return new LlmResult.SourceUnavailable(
                        SOURCE_LABEL, EMPTY_COMPLETION_MESSAGE, UNAVAILABLE_HINT);
            }
            return new LlmResult.Success(text);
        } catch (Exception exception) {
            return new LlmResult.SourceUnavailable(
                    SOURCE_LABEL,
                    exception.getMessage() == null ? REQUEST_FAILED_MESSAGE : exception.getMessage(),
                    UNAVAILABLE_HINT);
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

    private static String extractAssistantText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        String text = response.getResult().getOutput().getText();
        return text == null ? "" : text.trim();
    }
}
