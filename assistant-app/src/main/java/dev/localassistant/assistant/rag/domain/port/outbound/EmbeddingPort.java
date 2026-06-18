package dev.localassistant.assistant.rag.domain.port.outbound;

import dev.localassistant.assistant.rag.domain.EmbeddingResult;

public interface EmbeddingPort {

    EmbeddingResult embedDocument(String text);

    EmbeddingResult embedQuery(String text);
}
