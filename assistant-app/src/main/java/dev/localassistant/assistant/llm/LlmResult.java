package dev.localassistant.assistant.llm;

import dev.localassistant.assistant.tools.SourceUnavailability;
import java.util.Objects;

public sealed interface LlmResult {

    default SourceUnavailability asUnavailability() {
        return switch (this) {
            case Success ignored ->
                    throw new IllegalStateException("asUnavailability requires a failed result");
            case SourceUnavailable unavailable -> unavailable.unavailability();
        };
    }

    record Success(String text) implements LlmResult {
        public Success {
            Objects.requireNonNull(text, "text");
            if (text.isBlank()) {
                throw new IllegalArgumentException("text must not be blank");
            }
        }
    }

    record SourceUnavailable(SourceUnavailability unavailability) implements LlmResult {
        public SourceUnavailable {
            Objects.requireNonNull(unavailability, "unavailability");
        }

        public SourceUnavailable(String sourceLabel, String message, String hint) {
            this(new SourceUnavailability(sourceLabel, message, hint));
        }

        public String sourceLabel() {
            return unavailability.sourceLabel();
        }

        public String message() {
            return unavailability.message();
        }

        public String hint() {
            return unavailability.hint();
        }
    }
}
