package dev.localassistant.assistant.rag.domain;

import dev.localassistant.assistant.shared.SourceUnavailability;

import java.util.Objects;

public sealed interface ChunkStorageOutcome {

    record Stored(RagIngestionReport.Outcome outcome) implements ChunkStorageOutcome {
        public Stored {
            Objects.requireNonNull(outcome, "outcome");
            if (outcome == RagIngestionReport.Outcome.UNCHANGED) {
                throw new IllegalArgumentException(
                    "storeChunks never yields UNCHANGED; that outcome is decided before storing");
            }
        }
    }

    record Unavailable(SourceUnavailability unavailability) implements ChunkStorageOutcome {
        public Unavailable {
            Objects.requireNonNull(unavailability, "unavailability");
        }
    }
}
