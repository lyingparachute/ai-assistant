package dev.localassistant.assistant.rag.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeSnippetTest {

    @Test
    void fromRetrievalCarriesMandatoryRetrievalScore() {
        KnowledgeSnippet snippet =
                KnowledgeSnippet.fromRetrieval(
                        "chunk text", "https://example.com", "abc123", 1, 0.85);

        assertThat(snippet.retrievalScore()).isEqualTo(new RetrievalScore(0.85));
        assertThat(snippet.retrievalScore().value()).isEqualTo(0.85);
        assertThat(snippet.chunkIndex()).isEqualTo(1);
    }

    @Test
    void rejectsBlankChunkText() {
        assertThatThrownBy(
                        () -> KnowledgeSnippet.fromRetrieval(" ", "https://example.com", "abc123", 0, 0.5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeChunkIndex() {
        assertThatThrownBy(
                        () -> KnowledgeSnippet.fromRetrieval("text", "https://example.com", "abc123", -1, 0.5))
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
