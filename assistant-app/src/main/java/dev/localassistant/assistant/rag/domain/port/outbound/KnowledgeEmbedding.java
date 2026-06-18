package dev.localassistant.assistant.rag.domain.port.outbound;

import dev.localassistant.assistant.rag.domain.EmbeddingResult;
import org.apache.commons.lang3.StringUtils;

public interface KnowledgeEmbedding {

    EmbeddingResult embedDocument(DocumentCommand command);

    EmbeddingResult embedQuery(QueryCommand command);

    record DocumentCommand(String text) {

        public DocumentCommand {
            if (StringUtils.isBlank(text)) {
                throw new IllegalArgumentException("text must not be blank");
            }
        }
    }

    record QueryCommand(String text) {

        public QueryCommand {
            if (StringUtils.isBlank(text)) {
                throw new IllegalArgumentException("text must not be blank");
            }
        }
    }
}
