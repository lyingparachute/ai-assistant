package dev.localassistant.assistant.adapters.outbound.pgvector;

import dev.localassistant.assistant.rag.EmbeddingDimensions;

import java.util.Locale;
import java.util.StringJoiner;

final class PgvectorEmbeddingCodec {

    // Eight fractional digits preserve nomic-embed-text float32 precision in the pgvector text
    // literal without inflating the literal; pgvector parses it back into vector(768).
    private static final String VECTOR_COMPONENT_FORMAT = "%.8f";

    private PgvectorEmbeddingCodec() {
    }

    static String toVectorLiteral(float[] embedding) {
        EmbeddingDimensions.requireValidLength(embedding);
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (float value : embedding) {
            joiner.add(String.format(Locale.ROOT, VECTOR_COMPONENT_FORMAT, value));
        }
        return joiner.toString();
    }
}
