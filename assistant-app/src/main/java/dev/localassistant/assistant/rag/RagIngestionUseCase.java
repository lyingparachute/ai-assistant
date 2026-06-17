package dev.localassistant.assistant.rag;

import dev.localassistant.assistant.llm.EmbeddingPort;
import dev.localassistant.assistant.llm.EmbeddingResult;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class RagIngestionUseCase {

    private final EmbeddingPort embeddingPort;
    private final RagKnowledgePort ragKnowledgePort;
    private final ProductKnowledgePort productKnowledgePort;
    private final DeterministicTextChunker textChunker;
    private final Clock clock;

    public RagIngestionUseCase(
            EmbeddingPort embeddingPort,
            RagKnowledgePort ragKnowledgePort,
            ProductKnowledgePort productKnowledgePort,
            DeterministicTextChunker textChunker,
            Clock clock) {
        this.embeddingPort = Objects.requireNonNull(embeddingPort, "embeddingPort");
        this.ragKnowledgePort = Objects.requireNonNull(ragKnowledgePort, "ragKnowledgePort");
        this.productKnowledgePort = Objects.requireNonNull(productKnowledgePort, "productKnowledgePort");
        this.textChunker = Objects.requireNonNull(textChunker, "textChunker");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public RagIngestionResult ingest(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new IllegalArgumentException("sourceUrl must not be blank");
        }

        ProductPageResult pageResult = productKnowledgePort.fetchAndExtract(sourceUrl);
        if (pageResult instanceof ProductPageResult.SourceUnavailable unavailable) {
            return new RagIngestionResult.SourceUnavailable(
                    unavailable.sourceLabel(), unavailable.message(), unavailable.hint());
        }

        String extractedText = ((ProductPageResult.Success) pageResult).extractedText();
        String normalizedText = textChunker.normalizeWhitespace(extractedText);
        String contentHash = ContentHasher.sha256Hex(normalizedText);

        Optional<String> storedHash = ragKnowledgePort.findContentHashForSource(sourceUrl);
        if (storedHash.isPresent() && storedHash.orElseThrow().equals(contentHash)) {
            int chunkCount = ragKnowledgePort.countChunksForSource(sourceUrl);
            return new RagIngestionResult.Success(
                    new RagIngestionReport(
                            sourceUrl, contentHash, chunkCount, RagIngestionReport.Outcome.UNCHANGED));
        }

        List<TextChunk> textChunks = textChunker.chunk(normalizedText);
        Instant ingestionTimestamp = clock.instant();
        List<RagChunk> ragChunks = new ArrayList<>(textChunks.size());
        for (TextChunk textChunk : textChunks) {
            EmbeddingResult embeddingResult = embeddingPort.embedDocument(textChunk.chunkText());
            if (embeddingResult instanceof EmbeddingResult.SourceUnavailable unavailable) {
                return new RagIngestionResult.SourceUnavailable(
                        unavailable.sourceLabel(), unavailable.message(), unavailable.hint());
            }
            float[] embedding = ((EmbeddingResult.Success) embeddingResult).embedding();
            ragChunks.add(
                    new RagChunk(
                            textChunk.chunkText(),
                            embedding,
                            sourceUrl,
                            contentHash,
                            textChunk.chunkIndex(),
                            ingestionTimestamp));
        }

        return ragKnowledgePort.storeChunks(sourceUrl, contentHash, List.copyOf(ragChunks));
    }
}
