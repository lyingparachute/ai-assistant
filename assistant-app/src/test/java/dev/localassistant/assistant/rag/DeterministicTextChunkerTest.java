package dev.localassistant.assistant.rag;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeterministicTextChunkerTest {

    private final DeterministicTextChunker chunker = new DeterministicTextChunker(40, 10);

    @Test
    void normalizesWhitespace() {
        assertThat(chunker.normalizeWhitespace("  Fraud   Guard\n monitors\t fraud "))
                .isEqualTo("Fraud Guard monitors fraud");
    }

    @Test
    void producesStableChunkIndexesAcrossRuns() {
        String text = chunker.normalizeWhitespace(
                "Fraud Guard monitors suspicious transactions and helps prevent chargebacks.");

        List<TextChunk> firstRun = chunker.chunk(text);
        List<TextChunk> secondRun = chunker.chunk(text);

        assertThat(firstRun).isNotEmpty();
        assertThat(firstRun).extracting(TextChunk::chunkIndex)
                .containsExactlyElementsOf(IntStream.range(0, firstRun.size()).boxed().toList());
        assertThat(firstRun).extracting(TextChunk::chunkText).isEqualTo(secondRun.stream()
                .map(TextChunk::chunkText)
                .toList());
    }

    @Test
    void rejectsOverlapGreaterThanOrEqualToMaxSize() {
        assertThatThrownBy(() -> new DeterministicTextChunker(100, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
