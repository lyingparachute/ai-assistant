package dev.localassistant.assistant.rag.domain;

public record RetrievalScore(double value) {

    private static final double MIN_SCORE = 0.0;
    private static final double MAX_SCORE = 1.0;

    public RetrievalScore {
        if (value < MIN_SCORE || value > MAX_SCORE) {
            throw new IllegalArgumentException(
                "retrieval score must be between " + MIN_SCORE + " and " + MAX_SCORE);
        }
    }
}
