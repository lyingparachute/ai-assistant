package dev.localassistant.assistant.rag.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RagRetrievalResultTest {

    @Test
    void successCopiesSnippetList() {
        List<KnowledgeSnippet> snippets = new ArrayList<>();
        snippets.add(
                KnowledgeSnippet.fromRetrieval("text", "https://example.com", "hash", 0, 0.9));

        RagRetrievalResult.Success success = new RagRetrievalResult.Success(snippets);
        snippets.clear();

        assertThat(success.snippets()).hasSize(1);
        assertThat(success.snippets().getFirst().chunkText()).isEqualTo("text");
    }

    @Test
    void successRejectsEmptySnippetList() {
        assertThatThrownBy(() -> new RagRetrievalResult.Success(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NoRelevantKnowledge");
    }

    @Test
    void noRelevantKnowledgeIsDistinctOutcome() {
        RagRetrievalResult result = new RagRetrievalResult.NoRelevantKnowledge();

        assertThat(result).isInstanceOf(RagRetrievalResult.NoRelevantKnowledge.class);
    }

    @Test
    void sourceUnavailableRejectsBlankSourceLabel() {
        assertThatThrownBy(
                        () -> new RagRetrievalResult.SourceUnavailable(" ", "message", "hint"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
