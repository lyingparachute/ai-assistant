package dev.localassistant.assistant.rag;

import dev.localassistant.assistant.llm.EmbeddingPort;
import dev.localassistant.assistant.llm.EmbeddingResult;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class RagIngestionUseCase {

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
            return new RagIngestionResult.SourceUnavailable(unavailable.asUnavailability());
        }

        String extractedText = ((ProductPageResult.Success) pageResult).extractedText();
        String normalizedText = textChunker.normalizeWhitespace(extractedText);
        String contentHash = ContentHasher.sha256Hex(normalizedText);

        Optional<RagIngestionResult> earlyResult =
                decideFromStoredState(
                        ragKnowledgePort.findContentHashForSource(sourceUrl), sourceUrl, contentHash);
        if (earlyResult.isPresent()) {
            return earlyResult.get();
        }

        return embedAndStore(sourceUrl, contentHash, normalizedText);
    }

    private Optional<RagIngestionResult> decideFromStoredState(
            StoredSourceState storedState, String sourceUrl, String contentHash) {
        return switch (storedState) {
            case StoredSourceState.Unavailable unavailable ->
                    Optional.of(new RagIngestionResult.SourceUnavailable(unavailable.unavailability()));
            case StoredSourceState.Stored stored when stored.contentHash().equals(contentHash) ->
                    Optional.of(
                            new RagIngestionResult.Success(
                                    new RagIngestionReport(
                                            sourceUrl,
                                            contentHash,
                                            stored.chunkCount(),
                                            RagIngestionReport.Outcome.UNCHANGED)));
            case StoredSourceState.Stored ignored -> Optional.empty();
            case StoredSourceState.Absent ignored -> Optional.empty();
        };
    }

    private RagIngestionResult embedAndStore(
            String sourceUrl, String contentHash, String normalizedText) {
        List<TextChunk> textChunks = textChunker.chunk(normalizedText);
        Instant ingestionTimestamp = clock.instant();
        List<RagChunk> ragChunks = new ArrayList<>(textChunks.size());
        for (TextChunk textChunk : textChunks) {
            EmbeddingResult embeddingResult = embeddingPort.embedDocument(textChunk.chunkText());
            if (embeddingResult instanceof EmbeddingResult.SourceUnavailable unavailable) {
                return new RagIngestionResult.SourceUnavailable(unavailable.asUnavailability());
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

        ChunkStorageOutcome storageOutcome =
                ragKnowledgePort.storeChunks(sourceUrl, contentHash, List.copyOf(ragChunks));
        return switch (storageOutcome) {
            case ChunkStorageOutcome.Unavailable unavailable ->
                    new RagIngestionResult.SourceUnavailable(unavailable.unavailability());
            case ChunkStorageOutcome.Stored stored ->
                    new RagIngestionResult.Success(
                            new RagIngestionReport(
                                    sourceUrl, contentHash, ragChunks.size(), stored.outcome()));
        };
    }
}
