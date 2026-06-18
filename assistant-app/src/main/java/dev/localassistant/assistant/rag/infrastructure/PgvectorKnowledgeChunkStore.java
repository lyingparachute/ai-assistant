package dev.localassistant.assistant.rag.infrastructure;

import dev.localassistant.assistant.rag.domain.ChunkStorageOutcome;
import dev.localassistant.assistant.rag.domain.KnowledgeSimilarityMatch;
import dev.localassistant.assistant.rag.domain.RagIngestionReport;
import dev.localassistant.assistant.rag.domain.StoredSourceState;
import dev.localassistant.assistant.rag.domain.port.outbound.KnowledgeChunkStore;
import dev.localassistant.assistant.shared.SourceUnavailability;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public final class PgvectorKnowledgeChunkStore implements KnowledgeChunkStore {

    private static final String SOURCE_LABEL = "pgvector RAG";
    private static final String UNAVAILABLE_HINT =
        "Verify PostgreSQL with pgvector is running and the schema is initialized";

    private final PgvectorIngestionRepository repository;

    @Override
    public ChunkStorageOutcome storeChunks(final StoreChunksCommand command) {
        try {
            final var replacedRows =
                repository.replaceChunksForSource(
                    command.sourceUrl(), command.contentHash(), command.chunks());
            final var outcome =
                replacedRows > 0
                    ? RagIngestionReport.Outcome.REPLACED
                    : RagIngestionReport.Outcome.INGESTED;
            return new ChunkStorageOutcome.Stored(outcome);
        } catch (RuntimeException exception) {
            return new ChunkStorageOutcome.Unavailable(
                new SourceUnavailability(
                    SOURCE_LABEL, "pgvector chunk storage failed", UNAVAILABLE_HINT));
        }
    }

    @Override
    public StoredSourceState findContentHashForSource(final FindContentHashCommand command) {
        try {
            return repository.findStoredSourceState(command.sourceUrl());
        } catch (RuntimeException exception) {
            return new StoredSourceState.Unavailable(
                new SourceUnavailability(
                    SOURCE_LABEL, "pgvector content-hash lookup failed", UNAVAILABLE_HINT));
        }
    }

    @Override
    public List<KnowledgeSimilarityMatch> findSimilar(final FindSimilarCommand command) {
        return repository
            .findSimilarChunks(command.queryEmbedding(), command.topK(), command.minSimilarity())
            .stream()
            .map(
                match ->
                    new KnowledgeSimilarityMatch(
                        match.chunkText(),
                        match.sourceUrl(),
                        match.contentHash(),
                        match.chunkIndex(),
                        match.similarityScore()))
            .toList();
    }
}
