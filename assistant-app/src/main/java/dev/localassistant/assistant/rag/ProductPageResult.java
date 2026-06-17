package dev.localassistant.assistant.rag;

import dev.localassistant.assistant.tools.SourceUnavailability;

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

    record SourceUnavailable(SourceUnavailability unavailability) implements ProductPageResult {
        public SourceUnavailable {
            Objects.requireNonNull(unavailability, "unavailability");
        }

        public SourceUnavailable(String sourceLabel, String message, String hint) {
            this(new SourceUnavailability(sourceLabel, message, hint));
        }

        public SourceUnavailability asUnavailability() {
            return unavailability;
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
