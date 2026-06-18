package dev.localassistant.assistant.rag;

import java.util.Objects;

public record TextChunk(int chunkIndex, String chunkText) {

    public TextChunk {
        Objects.requireNonNull(chunkText, "chunkText");
        if (chunkText.isBlank()) {
            throw new IllegalArgumentException("chunkText must not be blank");
        }
        if (chunkIndex < 0) {
            throw new IllegalArgumentException("chunkIndex must not be negative");
        }
    }
}
