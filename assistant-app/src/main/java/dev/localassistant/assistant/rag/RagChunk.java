package dev.localassistant.assistant.rag;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

public record RagChunk(
        String chunkText,
        float[] embedding,
        String sourceUrl,
        String contentHash,
        int chunkIndex,
        Instant ingestionTimestamp) {

    public RagChunk {
        Objects.requireNonNull(chunkText, "chunkText");
        Objects.requireNonNull(embedding, "embedding");
        Objects.requireNonNull(sourceUrl, "sourceUrl");
        Objects.requireNonNull(contentHash, "contentHash");
        Objects.requireNonNull(ingestionTimestamp, "ingestionTimestamp");
        if (chunkText.isBlank()) {
            throw new IllegalArgumentException("chunkText must not be blank");
        }
        if (sourceUrl.isBlank()) {
            throw new IllegalArgumentException("sourceUrl must not be blank");
        }
        if (contentHash.isBlank()) {
            throw new IllegalArgumentException("contentHash must not be blank");
        }
        if (chunkIndex < 0) {
            throw new IllegalArgumentException("chunkIndex must not be negative");
        }
        EmbeddingDimensions.requireValidLength(embedding);
        embedding = Arrays.copyOf(embedding, embedding.length);
    }

    @Override
    public float[] embedding() {
        return Arrays.copyOf(embedding, embedding.length);
    }
}
