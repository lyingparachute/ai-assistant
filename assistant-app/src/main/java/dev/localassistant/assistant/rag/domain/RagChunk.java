package dev.localassistant.assistant.rag.domain;

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
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof RagChunk other)) {
            return false;
        }
        return chunkIndex == other.chunkIndex
            && chunkText.equals(other.chunkText)
            && sourceUrl.equals(other.sourceUrl)
            && contentHash.equals(other.contentHash)
            && ingestionTimestamp.equals(other.ingestionTimestamp)
            && Arrays.equals(embedding, other.embedding);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(chunkText, sourceUrl, contentHash, chunkIndex, ingestionTimestamp);
        result = 31 * result + Arrays.hashCode(embedding);
        return result;
    }

    @Override
    public String toString() {
        return "RagChunk[chunkText="
            + chunkText
            + ", embedding=float["
            + embedding.length
            + "]{contentHash="
            + Arrays.hashCode(embedding)
            + "}, sourceUrl="
            + sourceUrl
            + ", contentHash="
            + contentHash
            + ", chunkIndex="
            + chunkIndex
            + ", ingestionTimestamp="
            + ingestionTimestamp
            + ']';
    }

    @Override
    public float[] embedding() {
        return Arrays.copyOf(embedding, embedding.length);
    }
}
