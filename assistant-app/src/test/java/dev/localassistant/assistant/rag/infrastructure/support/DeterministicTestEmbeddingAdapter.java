package dev.localassistant.assistant.rag.infrastructure.support;

import dev.localassistant.assistant.rag.domain.port.outbound.EmbeddingPort;
import dev.localassistant.assistant.rag.domain.EmbeddingResult;
import dev.localassistant.assistant.rag.domain.EmbeddingDimensions;

import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;

public final class DeterministicTestEmbeddingAdapter implements EmbeddingPort {

    private static final String DOCUMENT_PREFIX = "search_document:";
    private static final String QUERY_PREFIX = "search_query:";
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[a-z0-9]+");

    @Override
    public EmbeddingResult embedDocument(String text) {
        return embedWithPrefix(DOCUMENT_PREFIX, text);
    }

    @Override
    public EmbeddingResult embedQuery(String text) {
        return embedWithPrefix(QUERY_PREFIX, text);
    }

    private EmbeddingResult embedWithPrefix(String prefix, String text) {
        float[] vector = new float[EmbeddingDimensions.VECTOR_SIZE];
        applyPrefixSignal(prefix, vector);
        for (String token : tokenize(text)) {
            int dimension = Math.floorMod(token.hashCode(), EmbeddingDimensions.VECTOR_SIZE);
            vector[dimension] += 1.0f;
        }
        return new EmbeddingResult.Success(normalize(vector));
    }

    private void applyPrefixSignal(String prefix, float[] vector) {
        for (int index = 0; index < prefix.length(); index++) {
            int dimension = Math.floorMod(prefix.charAt(index), EmbeddingDimensions.VECTOR_SIZE);
            vector[dimension] += 0.05f;
        }
    }

    private Iterable<String> tokenize(String text) {
        return () ->
                TOKEN_PATTERN
                        .matcher(text.toLowerCase(Locale.ROOT))
                        .results()
                        .map(match -> match.group())
                        .iterator();
    }

    private float[] normalize(float[] vector) {
        double sumSquares = 0.0;
        for (float value : vector) {
            sumSquares += value * value;
        }
        if (sumSquares == 0.0) {
            float[] fallback = new float[EmbeddingDimensions.VECTOR_SIZE];
            fallback[0] = 1.0f;
            return fallback;
        }
        float magnitude = (float) Math.sqrt(sumSquares);
        float[] normalized = new float[EmbeddingDimensions.VECTOR_SIZE];
        for (int index = 0; index < vector.length; index++) {
            normalized[index] = vector[index] / magnitude;
        }
        return Arrays.copyOf(normalized, normalized.length);
    }
}
