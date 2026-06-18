package dev.localassistant.assistant.rag.domain;

import dev.localassistant.assistant.shared.SourceUnavailability;

import java.util.Arrays;
import java.util.Objects;

public sealed interface EmbeddingResult {

    default SourceUnavailability asUnavailability() {
        return switch (this) {
            case final Success ignored -> throw new IllegalStateException("asUnavailability requires a failed result");
            case SourceUnavailable(final var unavailability) -> unavailability;
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

        public SourceUnavailable(final String sourceLabel, final String message, final String hint) {
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
