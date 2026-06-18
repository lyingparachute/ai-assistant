package dev.localassistant.assistant.rag.domain.port.outbound;

import dev.localassistant.assistant.rag.domain.ChunkStorageOutcome;
import dev.localassistant.assistant.rag.domain.KnowledgeSimilarityMatch;
import dev.localassistant.assistant.rag.domain.RagChunk;
import dev.localassistant.assistant.rag.domain.StoredSourceState;

import java.util.List;
import java.util.Objects;

public interface KnowledgeChunkStore {

    ChunkStorageOutcome storeChunks(StoreChunksCommand command);

    StoredSourceState findContentHashForSource(FindContentHashCommand command);

    List<KnowledgeSimilarityMatch> findSimilar(FindSimilarCommand command);

    record StoreChunksCommand(String sourceUrl, String contentHash, List<RagChunk> chunks) {

        public StoreChunksCommand {
            if (sourceUrl == null || sourceUrl.isBlank()) {
                throw new IllegalArgumentException("sourceUrl must not be blank");
            }
            if (contentHash == null || contentHash.isBlank()) {
                throw new IllegalArgumentException("contentHash must not be blank");
            }
            Objects.requireNonNull(chunks, "chunks");
            if (chunks.isEmpty()) {
                throw new IllegalArgumentException("chunks must not be empty");
            }
            chunks = List.copyOf(chunks);
        }
    }

    record FindContentHashCommand(String sourceUrl) {

        public FindContentHashCommand {
            if (sourceUrl == null || sourceUrl.isBlank()) {
                throw new IllegalArgumentException("sourceUrl must not be blank");
            }
        }
    }

    record FindSimilarCommand(float[] queryEmbedding, int topK, double minSimilarity) {

        public FindSimilarCommand {
            Objects.requireNonNull(queryEmbedding, "queryEmbedding");
            if (topK <= 0) {
                throw new IllegalArgumentException("topK must be positive");
            }
            if (minSimilarity < 0.0 || minSimilarity > 1.0) {
                throw new IllegalArgumentException("minSimilarity must be between 0 and 1");
            }
        }
    }
}
