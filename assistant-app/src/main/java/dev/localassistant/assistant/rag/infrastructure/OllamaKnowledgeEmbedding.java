package dev.localassistant.assistant.rag.infrastructure;

import dev.localassistant.assistant.rag.domain.EmbeddingResult;
import dev.localassistant.assistant.rag.domain.port.outbound.EmbeddingPort;
import dev.localassistant.assistant.rag.domain.port.outbound.KnowledgeEmbedding;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class OllamaKnowledgeEmbedding implements KnowledgeEmbedding {

    private final EmbeddingPort embeddingPort;

    @Override
    public EmbeddingResult embedDocument(final DocumentCommand command) {
        return embeddingPort.embedDocument(command.text());
    }

    @Override
    public EmbeddingResult embedQuery(final QueryCommand command) {
        return embeddingPort.embedQuery(command.text());
    }
}
