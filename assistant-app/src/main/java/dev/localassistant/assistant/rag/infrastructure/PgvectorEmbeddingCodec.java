package dev.localassistant.assistant.rag.infrastructure;

import dev.localassistant.assistant.rag.domain.EmbeddingDimensions;
import lombok.experimental.UtilityClass;

import java.util.Locale;
import java.util.StringJoiner;

@UtilityClass
final class PgvectorEmbeddingCodec {

    // Eight fractional digits preserve nomic-embed-text float32 precision in the pgvector text
    // literal without inflating the literal; pgvector parses it back into vector(768).
    private static final String VECTOR_COMPONENT_FORMAT = "%.8f";

    static String toVectorLiteral(final float[] embedding) {
        EmbeddingDimensions.requireValidLength(embedding);
        final var joiner = new StringJoiner(",", "[", "]");
        for (final float value : embedding) {
            joiner.add(String.format(Locale.ROOT, VECTOR_COMPONENT_FORMAT, value));
        }
        return joiner.toString();
    }
}
