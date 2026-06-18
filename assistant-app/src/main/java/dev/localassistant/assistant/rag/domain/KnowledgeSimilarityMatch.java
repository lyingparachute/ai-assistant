package dev.localassistant.assistant.rag.domain;

public record KnowledgeSimilarityMatch(
    String chunkText, String sourceUrl, String contentHash, int chunkIndex, double similarityScore) {

    public KnowledgeSimilarityMatch {
        if (chunkText == null || chunkText.isBlank()) {
            throw new IllegalArgumentException("chunkText must not be blank");
        }
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new IllegalArgumentException("sourceUrl must not be blank");
        }
        if (contentHash == null || contentHash.isBlank()) {
            throw new IllegalArgumentException("contentHash must not be blank");
        }
        if (chunkIndex < 0) {
            throw new IllegalArgumentException("chunkIndex must not be negative");
        }
    }
}
