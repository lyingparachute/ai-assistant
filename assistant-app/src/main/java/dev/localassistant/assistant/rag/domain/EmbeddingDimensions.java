package dev.localassistant.assistant.rag.domain;

import lombok.experimental.UtilityClass;

@UtilityClass
public class EmbeddingDimensions {

    public static final int VECTOR_SIZE = 768;

    public static boolean matches(final float[] embedding) {
        return embedding.length == VECTOR_SIZE;
    }

    public static void requireValidLength(final float[] embedding) {
        if (!matches(embedding)) {
            throw new IllegalArgumentException(
                "embedding length must be " + VECTOR_SIZE + " but was " + embedding.length);
        }
    }
}
