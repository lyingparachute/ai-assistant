package dev.localassistant.assistant.rag.domain;

public record ChunkingWindow(int maxChunkSize, int chunkOverlap) {

    public ChunkingWindow {
        if (maxChunkSize < 1) {
            throw new IllegalArgumentException("maxChunkSize must be positive");
        }
        if (chunkOverlap < 0) {
            throw new IllegalArgumentException("chunkOverlap must not be negative");
        }
        if (chunkOverlap >= maxChunkSize) {
            throw new IllegalArgumentException("chunkOverlap must be smaller than maxChunkSize");
        }
    }

    public static ChunkingWindow of(final int maxChunkSize, final int chunkOverlap) {
        return new ChunkingWindow(maxChunkSize, chunkOverlap);
    }
}
