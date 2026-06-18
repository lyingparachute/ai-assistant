package dev.localassistant.assistant.rag.domain;

import java.util.Objects;

record TextChunk(int chunkIndex, String chunkText) {

    TextChunk {
        Objects.requireNonNull(chunkText, "chunkText");
        if (chunkText.isBlank()) {
            throw new IllegalArgumentException("chunkText must not be blank");
        }
        if (chunkIndex < 0) {
            throw new IllegalArgumentException("chunkIndex must not be negative");
        }
    }
}
