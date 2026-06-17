package dev.localassistant.assistant.llm;

import dev.localassistant.assistant.rag.EmbeddingDimensions;

import java.util.Arrays;
import java.util.Objects;

public sealed interface EmbeddingResult {

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

    record SourceUnavailable(String sourceLabel, String message, String hint) implements EmbeddingResult {
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
