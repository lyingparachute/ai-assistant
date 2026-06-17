package dev.localassistant.assistant.rag;

import java.util.List;
import java.util.Optional;

public interface RagKnowledgePort {

    RagRetrievalResult retrieve(String questionText, RagRetrievalPolicy policy);

    RagIngestionResult storeChunks(String sourceUrl, String contentHash, List<RagChunk> chunks);

    Optional<String> findContentHashForSource(String sourceUrl);

    int countChunksForSource(String sourceUrl);
}
