package dev.localassistant.assistant.rag.infrastructure;

import dev.localassistant.assistant.rag.domain.EmbeddingDimensions;
import dev.localassistant.assistant.rag.domain.EmbeddingResult;
import dev.localassistant.assistant.rag.domain.port.outbound.EmbeddingPort;
import dev.localassistant.assistant.rag.infrastructure.config.AssistantEmbeddingProperties;
import org.apache.commons.lang3.StringUtils;
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

    public OllamaEmbeddingAdapter(final EmbeddingModel embeddingModel, final AssistantEmbeddingProperties properties) {
        this.embeddingModel = Objects.requireNonNull(embeddingModel, "embeddingModel");
        Objects.requireNonNull(properties, "properties");
        documentPrefix = properties.documentPrefix();
        queryPrefix = properties.queryPrefix();
    }

    @Override
    public EmbeddingResult embedDocument(final String text) {
        return embedWithPrefix(documentPrefix, text);
    }

    @Override
    public EmbeddingResult embedQuery(final String text) {
        return embedWithPrefix(queryPrefix, text);
    }

    private EmbeddingResult embedWithPrefix(final String prefix, final String text) {
        try {
            final var embedding = embeddingModel.embed(prefix + text);
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
                StringUtils.defaultIfBlank(exception.getMessage(), "Ollama embedding request failed"),
                UNAVAILABLE_HINT);
        }
    }
}
