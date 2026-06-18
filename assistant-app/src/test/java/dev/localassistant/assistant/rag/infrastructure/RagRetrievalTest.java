package dev.localassistant.assistant.rag.infrastructure;

import dev.localassistant.assistant.rag.domain.EmbeddingResult;
import dev.localassistant.assistant.rag.domain.EmbeddingDimensions;
import dev.localassistant.assistant.rag.domain.RagRetrievalPolicy;
import dev.localassistant.assistant.rag.domain.RagRetrievalResult;
import dev.localassistant.assistant.rag.domain.KnowledgeSimilarityMatch;
import dev.localassistant.assistant.rag.domain.port.inbound.RetrieveRagKnowledge;
import dev.localassistant.assistant.rag.domain.port.outbound.KnowledgeChunkStore;
import dev.localassistant.assistant.rag.domain.port.outbound.KnowledgeEmbedding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RagRetrievalTest {

    private static final String PGVECTOR_SOURCE_LABEL = "pgvector RAG";
    private static final String EMBEDDING_SOURCE_LABEL = "Ollama embedding";
    private static final RagRetrievalPolicy POLICY = new RagRetrievalPolicy(3, 0.3);

    @Test
    void returnsSourceUnavailableFromEmbeddingFailureWithoutQueryingStore() {
        KnowledgeChunkStore knowledgeChunkStore = mock(KnowledgeChunkStore.class);
        KnowledgeEmbedding knowledgeEmbedding =
                knowledgeEmbedding(
                        new EmbeddingResult.SourceUnavailable(
                                EMBEDDING_SOURCE_LABEL, "embedding service offline", "start Ollama"));
        RagRetrieval retrieval = new RagRetrieval(knowledgeEmbedding, knowledgeChunkStore);

        RagRetrievalResult result =
                retrieval.execute(new RetrieveRagKnowledge.Command("how does fraud guard work", POLICY));

        assertThat(result).isInstanceOf(RagRetrievalResult.SourceUnavailable.class);
        RagRetrievalResult.SourceUnavailable unavailable = (RagRetrievalResult.SourceUnavailable) result;
        assertThat(unavailable.sourceLabel()).isEqualTo(EMBEDDING_SOURCE_LABEL);
    }

    @Test
    void returnsSourceUnavailableWhenChunkStoreQueryThrows() {
        KnowledgeChunkStore knowledgeChunkStore = mock(KnowledgeChunkStore.class);
        when(knowledgeChunkStore.findSimilar(any(KnowledgeChunkStore.FindSimilarCommand.class)))
                .thenThrow(new RuntimeException("connection reset by peer"));
        KnowledgeEmbedding knowledgeEmbedding =
                knowledgeEmbedding(new EmbeddingResult.Success(validEmbedding()));
        RagRetrieval retrieval = new RagRetrieval(knowledgeEmbedding, knowledgeChunkStore);

        RagRetrievalResult result =
                retrieval.execute(new RetrieveRagKnowledge.Command("how does fraud guard work", POLICY));

        assertThat(result).isInstanceOf(RagRetrievalResult.SourceUnavailable.class);
        RagRetrievalResult.SourceUnavailable unavailable = (RagRetrievalResult.SourceUnavailable) result;
        assertThat(unavailable.sourceLabel()).isEqualTo(PGVECTOR_SOURCE_LABEL);
    }

    @Test
    void returnsSuccessWhenStoreFindsSimilarChunks() {
        KnowledgeChunkStore knowledgeChunkStore = mock(KnowledgeChunkStore.class);
        when(knowledgeChunkStore.findSimilar(any(KnowledgeChunkStore.FindSimilarCommand.class)))
                .thenReturn(
                        List.of(
                                new KnowledgeSimilarityMatch(
                                        "Fraud Guard monitors suspicious transactions.",
                                        "https://example.test/page",
                                        "hash",
                                        0,
                                        0.82)));
        KnowledgeEmbedding knowledgeEmbedding =
                knowledgeEmbedding(new EmbeddingResult.Success(validEmbedding()));
        RagRetrieval retrieval = new RagRetrieval(knowledgeEmbedding, knowledgeChunkStore);

        RagRetrievalResult result =
                retrieval.execute(new RetrieveRagKnowledge.Command("how does fraud guard work", POLICY));

        assertThat(result).isInstanceOf(RagRetrievalResult.Success.class);
    }

    private static KnowledgeEmbedding knowledgeEmbedding(EmbeddingResult queryResult) {
        return new KnowledgeEmbedding() {
            @Override
            public EmbeddingResult embedDocument(KnowledgeEmbedding.DocumentCommand command) {
                return queryResult;
            }

            @Override
            public EmbeddingResult embedQuery(KnowledgeEmbedding.QueryCommand command) {
                return queryResult;
            }
        };
    }

    private static float[] validEmbedding() {
        float[] embedding = new float[EmbeddingDimensions.VECTOR_SIZE];
        embedding[0] = 0.5f;
        return embedding;
    }
}
