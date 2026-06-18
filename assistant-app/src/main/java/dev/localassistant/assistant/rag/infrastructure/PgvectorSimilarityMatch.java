package dev.localassistant.assistant.rag.infrastructure;

record PgvectorSimilarityMatch(
    String chunkText,
    String sourceUrl,
    String contentHash,
    int chunkIndex,
    double similarityScore) {
}
