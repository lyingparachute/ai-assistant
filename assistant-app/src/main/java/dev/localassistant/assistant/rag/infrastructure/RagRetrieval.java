package dev.localassistant.assistant.rag.infrastructure;

import dev.localassistant.assistant.rag.domain.EmbeddingResult;
import dev.localassistant.assistant.rag.domain.KnowledgeSnippet;
import dev.localassistant.assistant.rag.domain.RagRetrievalResult;
import dev.localassistant.assistant.rag.domain.port.inbound.RetrieveRagKnowledge;
import dev.localassistant.assistant.rag.domain.port.outbound.KnowledgeChunkStore;
import dev.localassistant.assistant.rag.domain.port.outbound.KnowledgeEmbedding;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class RagRetrieval implements RetrieveRagKnowledge {

    private static final String SOURCE_LABEL = "pgvector RAG";
    private static final String UNAVAILABLE_HINT =
        "Verify PostgreSQL with pgvector is running and the schema is initialized";

    private final KnowledgeEmbedding knowledgeEmbedding;
    private final KnowledgeChunkStore knowledgeChunkStore;

    @Override
    public RagRetrievalResult execute(final Command command) {
        return switch (knowledgeEmbedding.embedQuery(new KnowledgeEmbedding.QueryCommand(command.questionText()))) {
            case EmbeddingResult.SourceUnavailable(final var unavailability) -> new RagRetrievalResult.SourceUnavailable(unavailability);
            case EmbeddingResult.Success(final var queryEmbedding) -> {
                try {
                    final var matches =
                        knowledgeChunkStore.findSimilar(
                            new KnowledgeChunkStore.FindSimilarCommand(
                                queryEmbedding,
                                command.policy().topK(),
                                command.policy().relevanceThreshold()));
                    if (matches.isEmpty()) {
                        yield new RagRetrievalResult.NoRelevantKnowledge();
                    }

                    final var snippets =
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
                    yield new RagRetrievalResult.Success(snippets);
                } catch (RuntimeException exception) {
                    yield new RagRetrievalResult.SourceUnavailable(
                        SOURCE_LABEL, "pgvector similarity search failed", UNAVAILABLE_HINT);
                }
            }
        };
    }
}
