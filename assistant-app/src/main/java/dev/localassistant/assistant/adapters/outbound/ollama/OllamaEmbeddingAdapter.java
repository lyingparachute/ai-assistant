package dev.localassistant.assistant.adapters.outbound.ollama;

import dev.localassistant.assistant.config.AssistantEmbeddingProperties;
import dev.localassistant.assistant.llm.EmbeddingPort;
import dev.localassistant.assistant.llm.EmbeddingResult;
import dev.localassistant.assistant.rag.EmbeddingDimensions;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.Objects;

public final class OllamaEmbeddingAdapter implements EmbeddingPort {

    static final String SOURCE_LABEL = "Ollama embedding";
    static final String UNAVAILABLE_HINT =
            "Ensure Ollama is running and the configured embedding model is pulled. "
                    + "Retry later; do not invent product knowledge.";

    private final EmbeddingModel embeddingModel;
    private final String documentPrefix;
    private final String queryPrefix;

    public OllamaEmbeddingAdapter(EmbeddingModel embeddingModel, AssistantEmbeddingProperties properties) {
        this.embeddingModel = Objects.requireNonNull(embeddingModel, "embeddingModel");
        Objects.requireNonNull(properties, "properties");
        this.documentPrefix = properties.documentPrefix();
        this.queryPrefix = properties.queryPrefix();
    }

    @Override
    public EmbeddingResult embedDocument(String text) {
        return embedWithPrefix(documentPrefix, text);
    }

    @Override
    public EmbeddingResult embedQuery(String text) {
        return embedWithPrefix(queryPrefix, text);
    }

    private EmbeddingResult embedWithPrefix(String prefix, String text) {
        try {
            float[] embedding = embeddingModel.embed(prefix + text);
            if (!EmbeddingDimensions.matches(embedding)) {
                return new EmbeddingResult.SourceUnavailable(
                        SOURCE_LABEL,
                        "embedding length must be "
                                + EmbeddingDimensions.VECTOR_SIZE
                                + " but was "
                                + embedding.length,
                        UNAVAILABLE_HINT);
            }
            return new EmbeddingResult.Success(embedding);
        } catch (RuntimeException exception) {
            return new EmbeddingResult.SourceUnavailable(
                    SOURCE_LABEL,
                    exception.getMessage() == null ? "Ollama embedding request failed" : exception.getMessage(),
                    UNAVAILABLE_HINT);
        }
    }
}
