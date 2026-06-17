package dev.localassistant.assistant.adapters.outbound.ollama;

import dev.localassistant.assistant.config.AssistantEmbeddingProperties;
import dev.localassistant.assistant.llm.EmbeddingResult;
import dev.localassistant.assistant.rag.EmbeddingDimensions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OllamaEmbeddingAdapterContractTest {

    private static final String DOCUMENT_PREFIX = "search_document:";
    private static final String QUERY_PREFIX = "search_query:";

    @Mock
    private EmbeddingModel embeddingModel;

    private OllamaEmbeddingAdapter adapter;

    @BeforeEach
    void setUp() {
        AssistantEmbeddingProperties properties = new AssistantEmbeddingProperties(
                "http://localhost:11434", "nomic-embed-text", DOCUMENT_PREFIX, QUERY_PREFIX);
        adapter = new OllamaEmbeddingAdapter(embeddingModel, properties);
    }

    @Test
    void embedDocument_prependsDocumentPrefixBeforeCallingModel() {
        float[] documentEmbedding = signedEmbedding(0.11f);
        when(embeddingModel.embed(DOCUMENT_PREFIX + "fraud guard")).thenReturn(documentEmbedding);

        EmbeddingResult result = adapter.embedDocument("fraud guard");

        assertThat(result).isInstanceOf(EmbeddingResult.Success.class);
        assertThat(((EmbeddingResult.Success) result).embedding()).containsExactly(documentEmbedding);
    }

    @Test
    void embedQuery_prependsQueryPrefixBeforeCallingModel() {
        float[] queryEmbedding = signedEmbedding(0.22f);
        when(embeddingModel.embed(QUERY_PREFIX + "what is fraud guard")).thenReturn(queryEmbedding);

        EmbeddingResult result = adapter.embedQuery("what is fraud guard");

        assertThat(result).isInstanceOf(EmbeddingResult.Success.class);
        assertThat(((EmbeddingResult.Success) result).embedding()).containsExactly(queryEmbedding);
    }

    @Test
    void returnsSuccessWhenEmbeddingHasExpectedDimension() {
        float[] embedding = validEmbedding();
        when(embeddingModel.embed(DOCUMENT_PREFIX + "chunk")).thenReturn(embedding);

        EmbeddingResult result = adapter.embedDocument("chunk");

        assertThat(result).isInstanceOf(EmbeddingResult.Success.class);
        assertThat(((EmbeddingResult.Success) result).embedding()).hasSize(EmbeddingDimensions.VECTOR_SIZE);
    }

    @Test
    void returnsSourceUnavailableWhenEmbeddingDimensionIsInvalid() {
        float[] wrongSizeEmbedding = new float[EmbeddingDimensions.VECTOR_SIZE / 2];
        when(embeddingModel.embed(QUERY_PREFIX + "question")).thenReturn(wrongSizeEmbedding);

        EmbeddingResult result = adapter.embedQuery("question");

        assertThat(result).isInstanceOf(EmbeddingResult.SourceUnavailable.class);
        EmbeddingResult.SourceUnavailable unavailable = (EmbeddingResult.SourceUnavailable) result;
        assertThat(unavailable.sourceLabel()).isEqualTo(OllamaEmbeddingAdapter.SOURCE_LABEL);
        assertThat(unavailable.message()).contains(String.valueOf(EmbeddingDimensions.VECTOR_SIZE));
    }

    @Test
    void returnsSourceUnavailableWhenModelThrows() {
        when(embeddingModel.embed(DOCUMENT_PREFIX + "chunk"))
                .thenThrow(new RuntimeException("connection refused"));

        EmbeddingResult result = adapter.embedDocument("chunk");

        assertThat(result).isInstanceOf(EmbeddingResult.SourceUnavailable.class);
        EmbeddingResult.SourceUnavailable unavailable = (EmbeddingResult.SourceUnavailable) result;
        assertThat(unavailable.sourceLabel()).isEqualTo(OllamaEmbeddingAdapter.SOURCE_LABEL);
        assertThat(unavailable.message()).isEqualTo("connection refused");
        assertThat(unavailable.hint()).isEqualTo(OllamaEmbeddingAdapter.UNAVAILABLE_HINT);
    }

    private static float[] validEmbedding() {
        float[] embedding = new float[EmbeddingDimensions.VECTOR_SIZE];
        embedding[0] = 0.1f;
        return embedding;
    }

    private static float[] signedEmbedding(float signature) {
        float[] embedding = new float[EmbeddingDimensions.VECTOR_SIZE];
        embedding[0] = signature;
        return embedding;
    }
}
