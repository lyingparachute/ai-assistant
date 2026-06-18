package dev.localassistant.assistant.rag;

import lombok.experimental.UtilityClass;

@UtilityClass
public class EmbeddingDimensions {

    public static final int VECTOR_SIZE = 768;

    public static boolean matches(float[] embedding) {
        return embedding.length == VECTOR_SIZE;
    }

    public static void requireValidLength(float[] embedding) {
        if (!matches(embedding)) {
            throw new IllegalArgumentException(
                    "embedding length must be " + VECTOR_SIZE + " but was " + embedding.length);
        }
    }
}
