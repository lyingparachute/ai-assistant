package dev.localassistant.assistant.rag.domain;

public record RagRetrievalPolicy(int topK, double relevanceThreshold) {

    private static final int MIN_TOP_K = 1;
    private static final double MIN_RELEVANCE_THRESHOLD = 0.0;
    private static final double MAX_RELEVANCE_THRESHOLD = 1.0;

    public RagRetrievalPolicy {
        if (topK < MIN_TOP_K) {
            throw new IllegalArgumentException("topK must be at least " + MIN_TOP_K);
        }
        if (relevanceThreshold < MIN_RELEVANCE_THRESHOLD || relevanceThreshold > MAX_RELEVANCE_THRESHOLD) {
            throw new IllegalArgumentException(
                "relevanceThreshold must be between "
                    + MIN_RELEVANCE_THRESHOLD
                    + " and "
                    + MAX_RELEVANCE_THRESHOLD);
        }
    }
}
