package dev.localassistant.assistant.rag.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public final class DeterministicTextChunker {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final int maxChunkSize;
    private final int chunkOverlap;

    public DeterministicTextChunker(final ChunkingWindow chunkingWindow) {
        Objects.requireNonNull(chunkingWindow, "chunkingWindow");
        maxChunkSize = chunkingWindow.maxChunkSize();
        chunkOverlap = chunkingWindow.chunkOverlap();
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

        final var chunks = new ArrayList<TextChunk>();
        int start = 0;
        int chunkIndex = 0;
        while (start < normalizedText.length()) {
            final var hardEnd = Math.min(start + maxChunkSize, normalizedText.length());
            final var end = resolveChunkEnd(normalizedText, start, hardEnd);
            final var chunkText = normalizedText.substring(start, end);
            chunks.add(new TextChunk(chunkIndex, chunkText));
            chunkIndex++;
            if (end >= normalizedText.length()) {
                break;
            }
            start = nextChunkStart(normalizedText, start, end);
        }
        return List.copyOf(chunks);
    }

    private int nextChunkStart(final String text, final int currentStart, final int end) {
        int nextStart = Math.max(end - chunkOverlap, currentStart + 1);
        while (nextStart < text.length() && text.charAt(nextStart) == ' ') {
            nextStart++;
        }
        return nextStart;
    }

    private static int resolveChunkEnd(final String text, final int start, final int hardEnd) {
        if (hardEnd >= text.length()) {
            return text.length();
        }
        for (int index = hardEnd - 1; index > start; index--) {
            final char current = text.charAt(index);
            if (current == '.' || current == '!' || current == '?') {
                return index + 1;
            }
        }
        return hardEnd;
    }
}
