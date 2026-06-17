package dev.localassistant.assistant.rag;

public final class EmbeddingDimensions {

    public static final int VECTOR_SIZE = 768;

    private EmbeddingDimensions() {
    }

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
