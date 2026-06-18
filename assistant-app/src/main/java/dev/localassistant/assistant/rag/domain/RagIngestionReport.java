package dev.localassistant.assistant.rag.domain;

import java.util.Objects;

public record RagIngestionReport(
    String sourceUrl, String contentHash, int chunkCount, Outcome outcome) {

    public enum Outcome {
        INGESTED,
        REPLACED,
        UNCHANGED
    }

    public RagIngestionReport {
        Objects.requireNonNull(sourceUrl, "sourceUrl");
        Objects.requireNonNull(contentHash, "contentHash");
        Objects.requireNonNull(outcome, "outcome");
        if (sourceUrl.isBlank()) {
            throw new IllegalArgumentException("sourceUrl must not be blank");
        }
        if (contentHash.isBlank()) {
            throw new IllegalArgumentException("contentHash must not be blank");
        }
        if (chunkCount < 0) {
            throw new IllegalArgumentException("chunkCount must not be negative");
        }
        if (chunkCount == 0) {
            throw new IllegalArgumentException(
                "chunkCount must be positive when outcome is " + outcome);
        }
    }
}
