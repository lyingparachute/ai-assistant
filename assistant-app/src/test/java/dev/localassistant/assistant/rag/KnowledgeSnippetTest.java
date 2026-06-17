package dev.localassistant.assistant.rag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeSnippetTest {

    @Test
    void fromStoredChunkHasNoSimilarityScore() {
        KnowledgeSnippet snippet =
                KnowledgeSnippet.fromStoredChunk("chunk text", "https://example.com", "abc123", 0);

        assertThat(snippet.similarityScore()).isEmpty();
        assertThat(snippet.chunkText()).isEqualTo("chunk text");
        assertThat(snippet.chunkIndex()).isZero();
    }

    @Test
    void fromRetrievalExposesSimilarityScore() {
        KnowledgeSnippet snippet =
                KnowledgeSnippet.fromRetrieval(
                        "chunk text", "https://example.com", "abc123", 1, 0.85);

        assertThat(snippet.similarityScore()).contains(0.85);
    }

    @Test
    void rejectsBlankChunkText() {
        assertThatThrownBy(
                        () -> KnowledgeSnippet.fromStoredChunk(" ", "https://example.com", "abc123", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeChunkIndex() {
        assertThatThrownBy(
                        () -> KnowledgeSnippet.fromStoredChunk("text", "https://example.com", "abc123", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsSimilarityScoreOutsideRange() {
        assertThatThrownBy(
                        () ->
                                KnowledgeSnippet.fromRetrieval(
                                        "text", "https://example.com", "abc123", 0, 1.1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
