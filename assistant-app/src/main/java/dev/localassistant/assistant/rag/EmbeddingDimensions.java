package dev.localassistant.assistant.rag;

public final class EmbeddingDimensions {

    public static final int VECTOR_SIZE = 768;

    private EmbeddingDimensions() {
    }

    public static void requireValidLength(float[] embedding) {
        if (embedding.length != VECTOR_SIZE) {
            throw new IllegalArgumentException(
                    "embedding length must be " + VECTOR_SIZE + " but was " + embedding.length);
        }
    }
}
