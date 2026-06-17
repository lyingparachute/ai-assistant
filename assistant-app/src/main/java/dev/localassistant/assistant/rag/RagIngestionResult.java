package dev.localassistant.assistant.rag;

import java.util.Objects;

public sealed interface RagIngestionResult {

    record Success(RagIngestionReport report) implements RagIngestionResult {
        public Success {
            Objects.requireNonNull(report, "report");
        }
    }

    record SourceUnavailable(String sourceLabel, String message, String hint) implements RagIngestionResult {
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
