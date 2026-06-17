package dev.localassistant.assistant.rag;

import java.util.Objects;
import java.util.Optional;

public record KnowledgeSnippet(
        String chunkText,
        String sourceUrl,
        String contentHash,
        int chunkIndex,
        double retrievalSimilarityScore,
        boolean hasRetrievalSimilarityScore) {

    private static final double MIN_SIMILARITY_SCORE = 0.0;
    private static final double MAX_SIMILARITY_SCORE = 1.0;

    public KnowledgeSnippet {
        Objects.requireNonNull(chunkText, "chunkText");
        Objects.requireNonNull(sourceUrl, "sourceUrl");
        Objects.requireNonNull(contentHash, "contentHash");
        if (chunkText.isBlank()) {
            throw new IllegalArgumentException("chunkText must not be blank");
        }
        if (sourceUrl.isBlank()) {
            throw new IllegalArgumentException("sourceUrl must not be blank");
        }
        if (contentHash.isBlank()) {
            throw new IllegalArgumentException("contentHash must not be blank");
        }
        if (chunkIndex < 0) {
            throw new IllegalArgumentException("chunkIndex must not be negative");
        }
        if (hasRetrievalSimilarityScore
                && (retrievalSimilarityScore < MIN_SIMILARITY_SCORE
                        || retrievalSimilarityScore > MAX_SIMILARITY_SCORE)) {
            throw new IllegalArgumentException(
                    "retrievalSimilarityScore must be between "
                            + MIN_SIMILARITY_SCORE
                            + " and "
                            + MAX_SIMILARITY_SCORE);
        }
    }

    public static KnowledgeSnippet fromStoredChunk(
            String chunkText, String sourceUrl, String contentHash, int chunkIndex) {
        return new KnowledgeSnippet(chunkText, sourceUrl, contentHash, chunkIndex, 0.0, false);
    }

    public static KnowledgeSnippet fromRetrieval(
            String chunkText,
            String sourceUrl,
            String contentHash,
            int chunkIndex,
            double similarityScore) {
        return new KnowledgeSnippet(chunkText, sourceUrl, contentHash, chunkIndex, similarityScore, true);
    }

    public Optional<Double> similarityScore() {
        return hasRetrievalSimilarityScore
                ? Optional.of(retrievalSimilarityScore)
                : Optional.empty();
    }
}
