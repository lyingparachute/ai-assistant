package dev.localassistant.assistant.rag.infrastructure;

import dev.localassistant.assistant.rag.domain.port.outbound.EmbeddingPort;
import dev.localassistant.assistant.rag.domain.EmbeddingResult;
import dev.localassistant.assistant.rag.domain.port.outbound.KnowledgeEmbedding;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(EmbeddingPort.class)
@RequiredArgsConstructor
final class OllamaKnowledgeEmbedding implements KnowledgeEmbedding {

    private final EmbeddingPort embeddingPort;

    @Override
    public EmbeddingResult embedDocument(DocumentCommand command) {
        return embeddingPort.embedDocument(command.text());
    }

    @Override
    public EmbeddingResult embedQuery(QueryCommand command) {
        return embeddingPort.embedQuery(command.text());
    }
}
