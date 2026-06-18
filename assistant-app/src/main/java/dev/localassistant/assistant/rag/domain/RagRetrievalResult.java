package dev.localassistant.assistant.rag.domain;

import dev.localassistant.assistant.shared.SourceUnavailability;

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

    record SourceUnavailable(SourceUnavailability unavailability) implements RagRetrievalResult {
        public SourceUnavailable {
            Objects.requireNonNull(unavailability, "unavailability");
        }

        public SourceUnavailable(final String sourceLabel, final String message, final String hint) {
            this(new SourceUnavailability(sourceLabel, message, hint));
        }

        public SourceUnavailability asUnavailability() {
            return unavailability;
        }

        public String sourceLabel() {
            return unavailability.sourceLabel();
        }

        public String message() {
            return unavailability.message();
        }

        public String hint() {
            return unavailability.hint();
        }
    }
}
