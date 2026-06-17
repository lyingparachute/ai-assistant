package dev.localassistant.assistant.adapters.outbound.pgvector;

import dev.localassistant.assistant.rag.EmbeddingDimensions;

import java.util.Locale;
import java.util.StringJoiner;

final class PgvectorEmbeddingCodec {

    private PgvectorEmbeddingCodec() {
    }

    static String toVectorLiteral(float[] embedding) {
        EmbeddingDimensions.requireValidLength(embedding);
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (float value : embedding) {
            joiner.add(String.format(Locale.ROOT, "%.8f", value));
        }
        return joiner.toString();
    }
}
