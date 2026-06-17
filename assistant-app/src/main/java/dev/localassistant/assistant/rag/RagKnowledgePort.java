package dev.localassistant.assistant.rag;

import java.util.List;

public interface RagKnowledgePort {

    RagRetrievalResult retrieve(String questionText, RagRetrievalPolicy policy);

    ChunkStorageOutcome storeChunks(String sourceUrl, String contentHash, List<RagChunk> chunks);

    StoredSourceState findContentHashForSource(String sourceUrl);
}
