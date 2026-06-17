package dev.localassistant.assistant.adapters.outbound.pgvector;

import dev.localassistant.assistant.llm.EmbeddingPort;
import dev.localassistant.assistant.llm.EmbeddingResult;
import dev.localassistant.assistant.rag.EmbeddingDimensions;
import dev.localassistant.assistant.rag.RagRetrievalPolicy;
import dev.localassistant.assistant.rag.RagRetrievalResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PgvectorRagAdapterRetrievalTest {

    private static final String PGVECTOR_SOURCE_LABEL = "pgvector RAG";
    private static final String EMBEDDING_SOURCE_LABEL = "Ollama embedding";
    private static final RagRetrievalPolicy POLICY = new RagRetrievalPolicy(3, 0.3);

    @Test
    void returnsSourceUnavailableFromEmbeddingFailureWithoutQueryingPgvector() {
        PgvectorIngestionRepository repository = mock(PgvectorIngestionRepository.class);
        EmbeddingPort embeddingPort =
                embeddingPort(
                        new EmbeddingResult.SourceUnavailable(
                                EMBEDDING_SOURCE_LABEL, "embedding service offline", "start Ollama"));
        PgvectorRagAdapter adapter = new PgvectorRagAdapter(repository, embeddingPort);

        RagRetrievalResult result = adapter.retrieve("how does fraud guard work", POLICY);

        assertThat(result).isInstanceOf(RagRetrievalResult.SourceUnavailable.class);
        RagRetrievalResult.SourceUnavailable unavailable = (RagRetrievalResult.SourceUnavailable) result;
        assertThat(unavailable.sourceLabel()).isEqualTo(EMBEDDING_SOURCE_LABEL);
    }

    @Test
    void returnsSourceUnavailableWhenPgvectorQueryThrows() {
        PgvectorIngestionRepository repository = mock(PgvectorIngestionRepository.class);
        when(repository.findSimilarChunks(any(), anyInt(), anyDouble()))
                .thenThrow(new RuntimeException("connection reset by peer"));
        EmbeddingPort embeddingPort = embeddingPort(new EmbeddingResult.Success(validEmbedding()));
        PgvectorRagAdapter adapter = new PgvectorRagAdapter(repository, embeddingPort);

        RagRetrievalResult result = adapter.retrieve("how does fraud guard work", POLICY);

        assertThat(result).isInstanceOf(RagRetrievalResult.SourceUnavailable.class);
        RagRetrievalResult.SourceUnavailable unavailable = (RagRetrievalResult.SourceUnavailable) result;
        assertThat(unavailable.sourceLabel()).isEqualTo(PGVECTOR_SOURCE_LABEL);
    }

    private static EmbeddingPort embeddingPort(EmbeddingResult queryResult) {
        return new EmbeddingPort() {
            @Override
            public EmbeddingResult embedDocument(String text) {
                return queryResult;
            }

            @Override
            public EmbeddingResult embedQuery(String text) {
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
