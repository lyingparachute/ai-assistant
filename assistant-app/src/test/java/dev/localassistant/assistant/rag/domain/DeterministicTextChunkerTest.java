package dev.localassistant.assistant.rag.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeterministicTextChunkerTest {

    private final DeterministicTextChunker chunker = new DeterministicTextChunker(ChunkingWindow.of(40, 10));

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
        assertThatThrownBy(() -> ChunkingWindow.of(100, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void consecutiveChunksShareOverlapWindow() {
        DeterministicTextChunker overlapChunker = new DeterministicTextChunker(ChunkingWindow.of(10, 3));
        String text = "ABCDEFGHIJKLMNOPQRST";

        List<TextChunk> chunks = overlapChunker.chunk(text);

        assertThat(chunks).extracting(TextChunk::chunkText)
                .containsExactly("ABCDEFGHIJ", "HIJKLMNOPQ", "OPQRST");
        String firstTail = chunks.get(0).chunkText().substring(7);
        String secondHead = chunks.get(1).chunkText().substring(0, 3);
        assertThat(secondHead).isEqualTo(firstTail).isEqualTo("HIJ");
    }

    @Test
    void exactMultipleLengthProducesNoTrailingEmptyChunk() {
        DeterministicTextChunker exactChunker = new DeterministicTextChunker(ChunkingWindow.of(10, 0));
        String text = "ABCDEFGHIJKLMNOPQRST";

        List<TextChunk> chunks = exactChunker.chunk(text);

        assertThat(chunks).extracting(TextChunk::chunkText)
                .containsExactly("ABCDEFGHIJ", "KLMNOPQRST");
        assertThat(chunks).noneMatch(chunk -> chunk.chunkText().isEmpty());
    }

    @Test
    void splitsAtSentenceBoundaryWithinMaxSize() {
        DeterministicTextChunker sentenceChunker = new DeterministicTextChunker(ChunkingWindow.of(60, 0));
        String text =
                "Fraud Guard monitors suspicious transactions. It helps prevent chargebacks for merchants.";

        List<TextChunk> chunks = sentenceChunker.chunk(text);

        assertThat(chunks).extracting(TextChunk::chunkText)
                .containsExactly(
                        "Fraud Guard monitors suspicious transactions.",
                        "It helps prevent chargebacks for merchants.");
    }

    @Test
    void doesNotSplitMidSentenceWhenFullTextFitsWithinMaxSize() {
        DeterministicTextChunker sentenceChunker = new DeterministicTextChunker(ChunkingWindow.of(80, 0));
        String text = "Short intro. Fraud Guard monitors suspicious transactions and chargebacks.";

        List<TextChunk> chunks = sentenceChunker.chunk(text);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().chunkText()).isEqualTo(text);
    }

    @Test
    void hardCutsOversizedSentenceWhenNoEarlierBoundaryExists() {
        DeterministicTextChunker smallChunker = new DeterministicTextChunker(ChunkingWindow.of(20, 0));
        String text = "This single sentence exceeds the configured chunk window.";

        List<TextChunk> chunks = smallChunker.chunk(text);

        assertThat(chunks).extracting(chunk -> chunk.chunkText().length()).allMatch(length -> length <= 20);
        assertThat(chunks).extracting(TextChunk::chunkText)
                .containsExactly("This single sentence", "exceeds the configur", "ed chunk window.");
        assertThat(chunks).noneMatch(chunk -> chunk.chunkText().isEmpty());
    }
}
