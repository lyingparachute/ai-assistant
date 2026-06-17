package dev.localassistant.assistant.adapters.outbound.pgvector;

record PgvectorSimilarityMatch(
        String chunkText,
        String sourceUrl,
        String contentHash,
        int chunkIndex,
        double similarityScore) {
}
