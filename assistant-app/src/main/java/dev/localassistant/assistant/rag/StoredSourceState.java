package dev.localassistant.assistant.rag;

import dev.localassistant.assistant.tools.SourceUnavailability;

import java.util.Objects;

public sealed interface StoredSourceState {

    record Stored(String contentHash, int chunkCount) implements StoredSourceState {
        public Stored {
            Objects.requireNonNull(contentHash, "contentHash");
            if (contentHash.isBlank()) {
                throw new IllegalArgumentException("contentHash must not be blank");
            }
            if (chunkCount < 1) {
                throw new IllegalArgumentException("chunkCount must be positive when a source is stored");
            }
        }
    }

    record Absent() implements StoredSourceState {
    }

    record Unavailable(SourceUnavailability unavailability) implements StoredSourceState {
        public Unavailable {
            Objects.requireNonNull(unavailability, "unavailability");
        }
    }
}
