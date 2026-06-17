package dev.localassistant.assistant.llm;

import dev.localassistant.assistant.rag.EmbeddingDimensions;
import dev.localassistant.assistant.tools.SourceUnavailability;

import java.util.Arrays;
import java.util.Objects;

public sealed interface EmbeddingResult {

    default SourceUnavailability asUnavailability() {
        return switch (this) {
            case Success ignored ->
                    throw new IllegalStateException("asUnavailability requires a failed result");
            case SourceUnavailable unavailable -> unavailable.unavailability();
        };
    }

    record Success(float[] embedding) implements EmbeddingResult {
        public Success {
            Objects.requireNonNull(embedding, "embedding");
            EmbeddingDimensions.requireValidLength(embedding);
            embedding = Arrays.copyOf(embedding, embedding.length);
        }

        @Override
        public float[] embedding() {
            return Arrays.copyOf(embedding, embedding.length);
        }
    }

    record SourceUnavailable(SourceUnavailability unavailability) implements EmbeddingResult {
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
