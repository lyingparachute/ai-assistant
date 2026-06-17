package dev.localassistant.assistant.adapters.outbound.pgvector;

import dev.localassistant.assistant.llm.EmbeddingPort;
import dev.localassistant.assistant.llm.EmbeddingResult;
import dev.localassistant.assistant.rag.KnowledgeSnippet;
import dev.localassistant.assistant.rag.RagChunk;
import dev.localassistant.assistant.rag.RagIngestionReport;
import dev.localassistant.assistant.rag.RagIngestionResult;
import dev.localassistant.assistant.rag.RagKnowledgePort;
import dev.localassistant.assistant.rag.RagRetrievalPolicy;
import dev.localassistant.assistant.rag.RagRetrievalResult;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class PgvectorRagAdapter implements RagKnowledgePort {

    private static final String SOURCE_LABEL = "pgvector RAG";

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
        if (questionText.isBlank()) {
            throw new IllegalArgumentException("questionText must not be blank");
        }

        EmbeddingResult embeddingResult = embeddingPort.embedQuery(questionText);
        if (embeddingResult instanceof EmbeddingResult.SourceUnavailable unavailable) {
            return new RagRetrievalResult.SourceUnavailable(
                    unavailable.sourceLabel(), unavailable.message(), unavailable.hint());
        }

        float[] queryEmbedding = ((EmbeddingResult.Success) embeddingResult).embedding();
        List<PgvectorSimilarityMatch> matches;
        try {
            matches =
                    repository.findSimilarChunks(
                            queryEmbedding, policy.topK(), policy.relevanceThreshold());
        } catch (RuntimeException exception) {
            return new RagRetrievalResult.SourceUnavailable(
                    SOURCE_LABEL,
                    "pgvector similarity search failed",
                    "Verify PostgreSQL with pgvector is running and the schema is initialized");
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
    public RagIngestionResult storeChunks(String sourceUrl, String contentHash, List<RagChunk> chunks) {
        Objects.requireNonNull(sourceUrl, "sourceUrl");
        Objects.requireNonNull(contentHash, "contentHash");
        Objects.requireNonNull(chunks, "chunks");
        if (sourceUrl.isBlank()) {
            throw new IllegalArgumentException("sourceUrl must not be blank");
        }
        if (contentHash.isBlank()) {
            throw new IllegalArgumentException("contentHash must not be blank");
        }
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("chunks must not be empty");
        }

        try {
            int existingCount = repository.countChunksForSource(sourceUrl);
            repository.replaceChunksForSource(sourceUrl, contentHash, chunks);
            RagIngestionReport.Outcome outcome =
                    existingCount > 0
                            ? RagIngestionReport.Outcome.REPLACED
                            : RagIngestionReport.Outcome.INGESTED;
            return new RagIngestionResult.Success(
                    new RagIngestionReport(sourceUrl, contentHash, chunks.size(), outcome));
        } catch (RuntimeException exception) {
            return new RagIngestionResult.SourceUnavailable(
                    SOURCE_LABEL,
                    "pgvector chunk storage failed",
                    "Verify PostgreSQL with pgvector is running and the schema is initialized");
        }
    }

    @Override
    public Optional<String> findContentHashForSource(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new IllegalArgumentException("sourceUrl must not be blank");
        }
        try {
            return repository.findContentHashForSource(sourceUrl);
        } catch (RuntimeException exception) {
            throw new PgvectorStorageException("pgvector content-hash lookup failed", exception);
        }
    }

    @Override
    public int countChunksForSource(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new IllegalArgumentException("sourceUrl must not be blank");
        }
        try {
            return repository.countChunksForSource(sourceUrl);
        } catch (RuntimeException exception) {
            throw new PgvectorStorageException("pgvector chunk count lookup failed", exception);
        }
    }

    public void initializeSchema() {
        repository.initializeSchema();
    }
}
