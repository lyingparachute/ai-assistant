package dev.localassistant.assistant.rag.domain;

import dev.localassistant.assistant.rag.domain.port.inbound.IngestRag;
import dev.localassistant.assistant.rag.domain.port.outbound.KnowledgeChunkStore;
import dev.localassistant.assistant.rag.domain.port.outbound.KnowledgeEmbedding;
import dev.localassistant.assistant.rag.domain.port.outbound.ProductPageSource;
import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
public final class RagIngestion implements IngestRag {

    private final KnowledgeEmbedding knowledgeEmbedding;
    private final KnowledgeChunkStore knowledgeChunkStore;
    private final ProductPageSource productPageSource;
    private final DeterministicTextChunker textChunker;
    private final Clock clock;

    @Override
    public RagIngestionResult execute(final Command command) {
        final var sourceUrl = command.sourceUrl();
        final var pageResult = productPageSource.fetchAndExtract(new ProductPageSource.Command(sourceUrl));
        return switch (pageResult) {
            case ProductPageResult.SourceUnavailable(final var unavailability) -> new RagIngestionResult.SourceUnavailable(unavailability);
            case ProductPageResult.Success(final var extractedText) -> {
                final var normalizedText = textChunker.normalizeWhitespace(extractedText);
                final var contentHash = ContentHasher.sha256Hex(normalizedText);
                yield decideFromStoredState(
                    knowledgeChunkStore.findContentHashForSource(
                        new KnowledgeChunkStore.FindContentHashCommand(sourceUrl)),
                    sourceUrl,
                    contentHash)
                    .orElseGet(() -> embedAndStore(sourceUrl, contentHash, normalizedText));
            }
        };
    }

    private Optional<RagIngestionResult> decideFromStoredState(
        final StoredSourceState storedState, final String sourceUrl, final String contentHash) {
        return switch (storedState) {
            case StoredSourceState.Unavailable(final var unavailability) -> Optional.of(new RagIngestionResult.SourceUnavailable(unavailability));
            case StoredSourceState.Stored(final var storedHash, final var chunkCount) when storedHash.equals(contentHash) -> Optional.of(
                new RagIngestionResult.Success(
                    new RagIngestionReport(
                        sourceUrl,
                        contentHash,
                        chunkCount,
                        RagIngestionReport.Outcome.UNCHANGED)));
            case final StoredSourceState.Stored ignored -> Optional.empty();
            case final StoredSourceState.Absent ignored -> Optional.empty();
        };
    }

    private RagIngestionResult embedAndStore(
        final String sourceUrl, final String contentHash, final String normalizedText) {
        final var textChunks = textChunker.chunk(normalizedText);
        final var ingestionTimestamp = clock.instant();
        final var ragChunks = new ArrayList<RagChunk>(textChunks.size());
        for (final var textChunk : textChunks) {
            switch (textChunk) {
                case TextChunk(final var chunkIndex, final var chunkText) -> {
                    final var embeddingResult =
                        knowledgeEmbedding.embedDocument(
                            new KnowledgeEmbedding.DocumentCommand(chunkText));
                    switch (embeddingResult) {
                        case EmbeddingResult.SourceUnavailable(final var unavailability) -> {
                            return new RagIngestionResult.SourceUnavailable(unavailability);
                        }
                        case EmbeddingResult.Success(final var embedding) -> ragChunks.add(
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
            knowledgeChunkStore.storeChunks(
                new KnowledgeChunkStore.StoreChunksCommand(sourceUrl, contentHash, storedChunks));
        return switch (storageOutcome) {
            case ChunkStorageOutcome.Unavailable(final var unavailability) -> new RagIngestionResult.SourceUnavailable(unavailability);
            case ChunkStorageOutcome.Stored(final var outcome) -> new RagIngestionResult.Success(
                new RagIngestionReport(
                    sourceUrl, contentHash, storedChunks.size(), outcome));
        };
    }
}
