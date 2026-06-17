package dev.localassistant.assistant.rag;

import java.util.Objects;

public sealed interface ProductPageResult {

    record Success(String extractedText) implements ProductPageResult {
        public Success {
            Objects.requireNonNull(extractedText, "extractedText");
            if (extractedText.isBlank()) {
                throw new IllegalArgumentException("extractedText must not be blank");
            }
        }
    }

    record SourceUnavailable(String sourceLabel, String message, String hint) implements ProductPageResult {
        public SourceUnavailable {
            Objects.requireNonNull(sourceLabel, "sourceLabel");
            Objects.requireNonNull(message, "message");
            Objects.requireNonNull(hint, "hint");
            if (sourceLabel.isBlank()) {
                throw new IllegalArgumentException("sourceLabel must not be blank");
            }
        }
    }
}
