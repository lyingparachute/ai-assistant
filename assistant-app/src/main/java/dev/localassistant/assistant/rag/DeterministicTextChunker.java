package dev.localassistant.assistant.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public final class DeterministicTextChunker {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final int maxChunkSize;
    private final int chunkOverlap;

    public DeterministicTextChunker(final int maxChunkSize, final int chunkOverlap) {
        if (maxChunkSize < 1) {
            throw new IllegalArgumentException("maxChunkSize must be positive");
        }
        if (chunkOverlap < 0) {
            throw new IllegalArgumentException("chunkOverlap must not be negative");
        }
        if (chunkOverlap >= maxChunkSize) {
            throw new IllegalArgumentException("chunkOverlap must be smaller than maxChunkSize");
        }
        this.maxChunkSize = maxChunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    public String normalizeWhitespace(final String text) {
        Objects.requireNonNull(text, "text");
        return WHITESPACE.matcher(text.trim()).replaceAll(" ");
    }

    public List<TextChunk> chunk(final String normalizedText) {
        Objects.requireNonNull(normalizedText, "normalizedText");
        if (normalizedText.isBlank()) {
            throw new IllegalArgumentException("normalizedText must not be blank");
        }

        var chunks = new ArrayList<TextChunk>();
        int start = 0;
        int chunkIndex = 0;
        while (start < normalizedText.length()) {
            final var end = Math.min(start + maxChunkSize, normalizedText.length());
            final var chunkText = normalizedText.substring(start, end);
            chunks.add(new TextChunk(chunkIndex, chunkText));
            chunkIndex++;
            if (end >= normalizedText.length()) {
                break;
            }
            start = end - chunkOverlap;
        }
        return List.copyOf(chunks);
    }
}
