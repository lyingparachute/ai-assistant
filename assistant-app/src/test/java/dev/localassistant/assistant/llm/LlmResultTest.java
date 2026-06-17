package dev.localassistant.assistant.llm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmResultTest {

    @Test
    void successRejectsBlankText() {
        assertThatThrownBy(() -> new LlmResult.Success(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("text");
    }

    @Test
    void successRejectsNullText() {
        assertThatThrownBy(() -> new LlmResult.Success(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("text");
    }

    @Test
    void sourceUnavailableRejectsBlankSourceLabel() {
        assertThatThrownBy(() -> new LlmResult.SourceUnavailable(" ", "message", "hint"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceLabel");
    }

    @Test
    void sourceUnavailableRejectsBlankMessage() {
        assertThatThrownBy(() -> new LlmResult.SourceUnavailable("Ollama chat", " ", "hint"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("message");
    }

    @Test
    void sourceUnavailableRejectsBlankHint() {
        assertThatThrownBy(() -> new LlmResult.SourceUnavailable("Ollama chat", "message", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hint");
    }
}
