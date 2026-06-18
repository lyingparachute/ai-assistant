package dev.localassistant.assistant.rag.domain.port.outbound;

import dev.localassistant.assistant.rag.domain.EmbeddingResult;

public interface KnowledgeEmbedding {

    EmbeddingResult embedDocument(DocumentCommand command);

    EmbeddingResult embedQuery(QueryCommand command);

    record DocumentCommand(String text) {

        public DocumentCommand {
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("text must not be blank");
            }
        }
    }

    record QueryCommand(String text) {

        public QueryCommand {
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("text must not be blank");
            }
        }
    }
}
