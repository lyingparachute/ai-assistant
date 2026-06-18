package dev.localassistant.assistant.rag.domain;

import dev.localassistant.assistant.shared.SourceUnavailability;

import java.util.Objects;

public sealed interface RagIngestionResult {

    record Success(RagIngestionReport report) implements RagIngestionResult {
        public Success {
            Objects.requireNonNull(report, "report");
        }
    }

    record SourceUnavailable(SourceUnavailability unavailability) implements RagIngestionResult {
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
