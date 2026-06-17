package dev.localassistant.assistant.rag;

import java.util.List;
import java.util.Objects;

public sealed interface RagRetrievalResult {

    record Success(List<KnowledgeSnippet> snippets) implements RagRetrievalResult {
        public Success {
            Objects.requireNonNull(snippets, "snippets");
            if (snippets.isEmpty()) {
                throw new IllegalArgumentException(
                        "snippets must not be empty; use NoRelevantKnowledge instead");
            }
            snippets = List.copyOf(snippets);
        }
    }

    record NoRelevantKnowledge() implements RagRetrievalResult {
    }

    record SourceUnavailable(String sourceLabel, String message, String hint) implements RagRetrievalResult {
        public SourceUnavailable {
            Objects.requireNonNull(sourceLabel, "sourceLabel");
            Objects.requireNonNull(message, "message");
            Objects.requireNonNull(hint, "hint");
            if (sourceLabel.isBlank()) {
                throw new IllegalArgumentException("sourceLabel must not be blank");
            }
        }
    }
}
