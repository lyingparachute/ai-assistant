package dev.localassistant.assistant.rag;

import dev.localassistant.assistant.llm.EmbeddingPort;
import dev.localassistant.assistant.llm.EmbeddingResult;
import dev.localassistant.assistant.rag.domain.ports.outbound.ProductKnowledgePort;

import java.time.Clock;
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

        final var pageResult = productKnowledgePort.fetchAndExtract(sourceUrl);
        return switch (pageResult) {
            case ProductPageResult.SourceUnavailable(var unavailability) ->
                    new RagIngestionResult.SourceUnavailable(unavailability);
            case ProductPageResult.Success(var extractedText) -> {
                final var normalizedText = textChunker.normalizeWhitespace(extractedText);
                final var contentHash = ContentHasher.sha256Hex(normalizedText);
                yield decideFromStoredState(
                                ragKnowledgePort.findContentHashForSource(sourceUrl),
                                sourceUrl,
                                contentHash)
                        .orElseGet(() -> embedAndStore(sourceUrl, contentHash, normalizedText));
            }
        };
    }

    private Optional<RagIngestionResult> decideFromStoredState(
            StoredSourceState storedState, String sourceUrl, String contentHash) {
        return switch (storedState) {
            case StoredSourceState.Unavailable(var unavailability) ->
                    Optional.of(new RagIngestionResult.SourceUnavailable(unavailability));
            case StoredSourceState.Stored(var storedHash, var chunkCount) when storedHash.equals(contentHash) ->
                    Optional.of(
                            new RagIngestionResult.Success(
                                    new RagIngestionReport(
                                            sourceUrl,
                                            contentHash,
                                            chunkCount,
                                            RagIngestionReport.Outcome.UNCHANGED)));
            case StoredSourceState.Stored ignored -> Optional.empty();
            case StoredSourceState.Absent ignored -> Optional.empty();
        };
    }

    private RagIngestionResult embedAndStore(
            String sourceUrl, String contentHash, String normalizedText) {
        final var textChunks = textChunker.chunk(normalizedText);
        final var ingestionTimestamp = clock.instant();
        final var ragChunks = new ArrayList<RagChunk>(textChunks.size());
        for (final var textChunk : textChunks) {
            switch (textChunk) {
                case TextChunk(var chunkIndex, var chunkText) -> {
                    final var embeddingResult = embeddingPort.embedDocument(chunkText);
                    switch (embeddingResult) {
                        case EmbeddingResult.SourceUnavailable(var unavailability) -> {
                            return new RagIngestionResult.SourceUnavailable(unavailability);
                        }
                        case EmbeddingResult.Success(var embedding) ->
                                ragChunks.add(
                                        new RagChunk(
                                                chunkText,
                                                embedding,
                                                sourceUrl,
                                                contentHash,
                                                chunkIndex,
                                                ingestionTimestamp));
                    }
                }
            }
        }

        final var storedChunks = List.copyOf(ragChunks);
        final var storageOutcome =
                ragKnowledgePort.storeChunks(sourceUrl, contentHash, storedChunks);
        return switch (storageOutcome) {
            case ChunkStorageOutcome.Unavailable(var unavailability) ->
                    new RagIngestionResult.SourceUnavailable(unavailability);
            case ChunkStorageOutcome.Stored(var outcome) ->
                    new RagIngestionResult.Success(
                            new RagIngestionReport(
                                    sourceUrl, contentHash, storedChunks.size(), outcome));
        };
    }
}
