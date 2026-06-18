package dev.localassistant.assistant.adapters.outbound.pgvector;

import dev.localassistant.assistant.llm.EmbeddingPort;
import dev.localassistant.assistant.llm.EmbeddingResult;
import dev.localassistant.assistant.rag.ChunkStorageOutcome;
import dev.localassistant.assistant.rag.KnowledgeSnippet;
import dev.localassistant.assistant.rag.RagChunk;
import dev.localassistant.assistant.rag.RagIngestionReport;
import dev.localassistant.assistant.rag.RagKnowledgePort;
import dev.localassistant.assistant.rag.RagRetrievalPolicy;
import dev.localassistant.assistant.rag.RagRetrievalResult;
import dev.localassistant.assistant.rag.StoredSourceState;
import dev.localassistant.assistant.tools.SourceUnavailability;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Objects;

public final class PgvectorRagAdapter implements RagKnowledgePort {

    private static final String SOURCE_LABEL = "pgvector RAG";
    private static final String UNAVAILABLE_HINT =
            "Verify PostgreSQL with pgvector is running and the schema is initialized";

    private final PgvectorIngestionRepository repository;
    private final EmbeddingPort embeddingPort;

    public PgvectorRagAdapter(PgvectorIngestionRepository repository, EmbeddingPort embeddingPort) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.embeddingPort = Objects.requireNonNull(embeddingPort, "embeddingPort");
    }

    @Override
    public RagRetrievalResult retrieve(String questionText, RagRetrievalPolicy policy) {
        Objects.requireNonNull(questionText, "questionText");
        Objects.requireNonNull(policy, "policy");
        if (StringUtils.isBlank(questionText)) {
            throw new IllegalArgumentException("questionText must not be blank");
        }

        EmbeddingResult embeddingResult = embeddingPort.embedQuery(questionText);
        if (embeddingResult instanceof EmbeddingResult.SourceUnavailable unavailable) {
            return new RagRetrievalResult.SourceUnavailable(unavailable.asUnavailability());
        }

        float[] queryEmbedding = ((EmbeddingResult.Success) embeddingResult).embedding();
        List<PgvectorSimilarityMatch> matches;
        try {
            matches =
                    repository.findSimilarChunks(
                            queryEmbedding, policy.topK(), policy.relevanceThreshold());
        } catch (RuntimeException exception) {
            return new RagRetrievalResult.SourceUnavailable(
                    SOURCE_LABEL, "pgvector similarity search failed", UNAVAILABLE_HINT);
        }

        if (matches.isEmpty()) {
            return new RagRetrievalResult.NoRelevantKnowledge();
        }

        List<KnowledgeSnippet> snippets =
                matches.stream()
                        .map(
                                match ->
                                        KnowledgeSnippet.fromRetrieval(
                                                match.chunkText(),
                                                match.sourceUrl(),
                                                match.contentHash(),
                                                match.chunkIndex(),
                                                match.similarityScore()))
                        .toList();
        return new RagRetrievalResult.Success(snippets);
    }

    @Override
    public ChunkStorageOutcome storeChunks(String sourceUrl, String contentHash, List<RagChunk> chunks) {
        Objects.requireNonNull(sourceUrl, "sourceUrl");
        Objects.requireNonNull(contentHash, "contentHash");
        Objects.requireNonNull(chunks, "chunks");
        if (StringUtils.isBlank(sourceUrl)) {
            throw new IllegalArgumentException("sourceUrl must not be blank");
        }
        if (StringUtils.isBlank(contentHash)) {
            throw new IllegalArgumentException("contentHash must not be blank");
        }
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("chunks must not be empty");
        }

        try {
            int replacedRows = repository.replaceChunksForSource(sourceUrl, contentHash, chunks);
            RagIngestionReport.Outcome outcome =
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
    public StoredSourceState findContentHashForSource(String sourceUrl) {
        if (StringUtils.isBlank(sourceUrl)) {
            throw new IllegalArgumentException("sourceUrl must not be blank");
        }
        try {
            return repository.findStoredSourceState(sourceUrl);
        } catch (RuntimeException exception) {
            return new StoredSourceState.Unavailable(
                    new SourceUnavailability(
                            SOURCE_LABEL, "pgvector content-hash lookup failed", UNAVAILABLE_HINT));
        }
    }
}
