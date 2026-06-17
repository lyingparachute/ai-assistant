package dev.localassistant.assistant.rag;

import java.util.Objects;

public record KnowledgeSnippet(
        String chunkText,
        String sourceUrl,
        String contentHash,
        int chunkIndex,
        RetrievalScore retrievalScore) {

    public KnowledgeSnippet {
        Objects.requireNonNull(chunkText, "chunkText");
        Objects.requireNonNull(sourceUrl, "sourceUrl");
        Objects.requireNonNull(contentHash, "contentHash");
        Objects.requireNonNull(retrievalScore, "retrievalScore");
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
    }

    public static KnowledgeSnippet fromRetrieval(
            String chunkText,
            String sourceUrl,
            String contentHash,
            int chunkIndex,
            double similarityScore) {
        return new KnowledgeSnippet(
                chunkText, sourceUrl, contentHash, chunkIndex, new RetrievalScore(similarityScore));
    }
}
