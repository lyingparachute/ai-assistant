package dev.localassistant.assistant.llm;

import java.util.Objects;

public sealed interface LlmResult {

    record Success(String text) implements LlmResult {
        public Success {
            Objects.requireNonNull(text, "text");
            if (text.isBlank()) {
                throw new IllegalArgumentException("text must not be blank");
            }
        }
    }

    record SourceUnavailable(String sourceLabel, String message, String hint) implements LlmResult {
        public SourceUnavailable {
            Objects.requireNonNull(sourceLabel, "sourceLabel");
            Objects.requireNonNull(message, "message");
            Objects.requireNonNull(hint, "hint");
            if (sourceLabel.isBlank()) {
                throw new IllegalArgumentException("sourceLabel must not be blank");
            }
            if (message.isBlank()) {
                throw new IllegalArgumentException("message must not be blank");
            }
            if (hint.isBlank()) {
                throw new IllegalArgumentException("hint must not be blank");
            }
        }
    }
}
