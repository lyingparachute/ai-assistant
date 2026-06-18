package dev.localassistant.assistant.rag.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RagChunkTest {

    @Test
    void acceptsValidChunk() {
        float[] embedding = validEmbedding();
        Instant ingestionTimestamp = Instant.parse("2026-06-15T10:00:00Z");

        RagChunk chunk =
                new RagChunk(
                        "chunk text",
                        embedding,
                        "https://example.com",
                        "hash-1",
                        2,
                        ingestionTimestamp);

        assertThat(chunk.chunkText()).isEqualTo("chunk text");
        assertThat(chunk.chunkIndex()).isEqualTo(2);
        assertThat(chunk.ingestionTimestamp()).isEqualTo(ingestionTimestamp);
        assertThat(chunk.embedding()).hasSize(EmbeddingDimensions.VECTOR_SIZE);
        assertThat(chunk.embedding()).isNotSameAs(embedding);
    }

    @Test
    void rejectsWrongEmbeddingLength() {
        float[] embedding = new float[EmbeddingDimensions.VECTOR_SIZE / 2];

        assertThatThrownBy(
                        () ->
                                new RagChunk(
                                        "chunk text",
                                        embedding,
                                        "https://example.com",
                                        "hash-1",
                                        0,
                                        Instant.parse("2026-06-15T10:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("768");
    }

    @Test
    void rejectsBlankChunkText() {
        assertThatThrownBy(
                        () ->
                                new RagChunk(
                                        " ",
                                        validEmbedding(),
                                        "https://example.com",
                                        "hash-1",
                                        0,
                                        Instant.parse("2026-06-15T10:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullIngestionTimestamp() {
        assertThatThrownBy(
                        () ->
                                new RagChunk(
                                        "chunk text",
                                        validEmbedding(),
                                        "https://example.com",
                                        "hash-1",
                                        0,
                                        null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("ingestionTimestamp");
    }

    @Test
    void defensiveCopyPreventsMutation() {
        float[] embedding = validEmbedding();
        RagChunk chunk =
                new RagChunk(
                        "chunk text",
                        embedding,
                        "https://example.com",
                        "hash-1",
                        0,
                        Instant.parse("2026-06-15T10:00:00Z"));

        Arrays.fill(embedding, 99.0f);

        assertThat(chunk.embedding()[0]).isNotEqualTo(99.0f);
    }

    @Test
    void equalsAndHashCodeConsiderEmbeddingContent() {
        Instant ingestionTimestamp = Instant.parse("2026-06-15T10:00:00Z");
        float[] firstEmbedding = validEmbedding();
        float[] matchingEmbedding = Arrays.copyOf(firstEmbedding, firstEmbedding.length);
        float[] differentEmbedding = validEmbedding();
        differentEmbedding[0] = 0.9f;

        RagChunk baseline =
                new RagChunk(
                        "chunk text",
                        firstEmbedding,
                        "https://example.com",
                        "hash-1",
                        0,
                        ingestionTimestamp);
        RagChunk matching =
                new RagChunk(
                        "chunk text",
                        matchingEmbedding,
                        "https://example.com",
                        "hash-1",
                        0,
                        ingestionTimestamp);
        RagChunk different =
                new RagChunk(
                        "chunk text",
                        differentEmbedding,
                        "https://example.com",
                        "hash-1",
                        0,
                        ingestionTimestamp);

        assertThat(baseline).isEqualTo(matching).hasSameHashCodeAs(matching);
        assertThat(baseline).isNotEqualTo(different);
        assertThat(baseline.toString()).contains("contentHash=").contains("float[768]");
    }

    private static float[] validEmbedding() {
        float[] embedding = new float[EmbeddingDimensions.VECTOR_SIZE];
        Arrays.fill(embedding, 0.1f);
        return embedding;
    }
}
