package dev.localassistant.assistant.rag;

import dev.localassistant.assistant.rag.domain.ChunkStorageOutcome;
import dev.localassistant.assistant.rag.domain.ChunkingWindow;
import dev.localassistant.assistant.rag.domain.DeterministicTextChunker;
import dev.localassistant.assistant.rag.domain.EmbeddingResult;
import dev.localassistant.assistant.rag.domain.KnowledgeSimilarityMatch;
import dev.localassistant.assistant.rag.domain.ProductPageResult;
import dev.localassistant.assistant.rag.domain.RagChunk;
import dev.localassistant.assistant.rag.domain.RagIngestion;
import dev.localassistant.assistant.rag.domain.RagIngestionReport;
import dev.localassistant.assistant.rag.domain.RagIngestionResult;
import dev.localassistant.assistant.rag.domain.StoredSourceState;
import dev.localassistant.assistant.rag.domain.port.inbound.IngestRag;
import dev.localassistant.assistant.rag.domain.port.outbound.EmbeddingPort;
import dev.localassistant.assistant.rag.domain.port.outbound.KnowledgeChunkStore;
import dev.localassistant.assistant.rag.domain.port.outbound.KnowledgeEmbedding;
import dev.localassistant.assistant.rag.domain.port.outbound.ProductPageSource;
import dev.localassistant.assistant.rag.infrastructure.support.DeterministicTestEmbeddingAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RagIngestionUseCaseTest {

    private static final String SOURCE_URL = "https://example.test/cdq-fraud-guard";
    private static final Instant INGESTED_AT = Instant.parse("2026-06-15T12:00:00Z");

    private StubProductPageSource productPageSource;
    private StubKnowledgeChunkStore knowledgeChunkStore;
    private CountingKnowledgeEmbedding knowledgeEmbedding;
    private RagIngestion ingestRag;

    @BeforeEach
    void setUp() {
        productPageSource = new StubProductPageSource();
        knowledgeChunkStore = new StubKnowledgeChunkStore();
        knowledgeEmbedding =
                new CountingKnowledgeEmbedding(new DeterministicTestEmbeddingAdapter());
        ingestRag =
                new RagIngestion(
                        knowledgeEmbedding,
                        knowledgeChunkStore,
                        productPageSource,
                        new DeterministicTextChunker(ChunkingWindow.of(80, 10)),
                        Clock.fixed(INGESTED_AT, ZoneOffset.UTC));
    }

    @Test
    void ingestsChunksWhenSourceIsNew() {
        productPageSource.text =
                "Fraud Guard monitors suspicious transactions and helps prevent chargebacks.";

        RagIngestionResult result = ingestRag.execute(new IngestRag.Command(SOURCE_URL));

        assertThat(result).isInstanceOf(RagIngestionResult.Success.class);
        RagIngestionReport report = ((RagIngestionResult.Success) result).report();
        assertThat(report.outcome()).isEqualTo(RagIngestionReport.Outcome.INGESTED);
        assertThat(report.chunkCount()).isPositive();
        assertThat(knowledgeChunkStore.storedChunks).hasSize(report.chunkCount());
    }

    @Test
    void skipsReEmbeddingWhenContentHashIsUnchanged() {
        productPageSource.text =
                "Fraud Guard monitors suspicious transactions and helps prevent chargebacks.";
        RagIngestionResult firstIngestion = ingestRag.execute(new IngestRag.Command(SOURCE_URL));
        RagIngestionReport firstReport = ((RagIngestionResult.Success) firstIngestion).report();
        int embedCallsAfterFirstIngestion = knowledgeEmbedding.documentEmbedCount();

        RagIngestionResult secondIngestion = ingestRag.execute(new IngestRag.Command(SOURCE_URL));
        RagIngestionReport secondReport = ((RagIngestionResult.Success) secondIngestion).report();

        assertThat(secondReport.outcome()).isEqualTo(RagIngestionReport.Outcome.UNCHANGED);
        assertThat(secondReport.contentHash()).isEqualTo(firstReport.contentHash());
        assertThat(secondReport.chunkCount()).isEqualTo(firstReport.chunkCount());
        assertThat(knowledgeChunkStore.storeInvocations).isEqualTo(1);
        assertThat(embedCallsAfterFirstIngestion).isEqualTo(firstReport.chunkCount());
        assertThat(knowledgeEmbedding.documentEmbedCount()).isEqualTo(embedCallsAfterFirstIngestion);
    }

    @Test
    void replacesStoredChunksWhenContentChanges() {
        productPageSource.text = "First version of Fraud Guard product knowledge.";
        ingestRag.execute(new IngestRag.Command(SOURCE_URL));

        productPageSource.text = "Updated Fraud Guard product knowledge with new details.";
        RagIngestionResult result = ingestRag.execute(new IngestRag.Command(SOURCE_URL));

        assertThat(result).isInstanceOf(RagIngestionResult.Success.class);
        assertThat(((RagIngestionResult.Success) result).report().outcome())
                .isEqualTo(RagIngestionReport.Outcome.REPLACED);
        assertThat(knowledgeChunkStore.storeInvocations).isEqualTo(2);
    }

    @Test
    void surfacesProductPageSourceUnavailable() {
        productPageSource.unavailable =
                new ProductPageResult.SourceUnavailable("CDQ product page", "fetch failed", "check URL");

        RagIngestionResult result = ingestRag.execute(new IngestRag.Command(SOURCE_URL));

        assertThat(result).isInstanceOf(RagIngestionResult.SourceUnavailable.class);
    }

    @Test
    void surfacesEmbeddingSourceUnavailableDuringIngestion() {
        productPageSource.text =
                "Fraud Guard monitors suspicious transactions and helps prevent chargebacks.";
        ingestRag =
                new RagIngestion(
                        new SourceUnavailableKnowledgeEmbedding(),
                        knowledgeChunkStore,
                        productPageSource,
                        new DeterministicTextChunker(ChunkingWindow.of(80, 10)),
                        Clock.fixed(INGESTED_AT, ZoneOffset.UTC));

        RagIngestionResult result = ingestRag.execute(new IngestRag.Command(SOURCE_URL));

        assertThat(result).isInstanceOf(RagIngestionResult.SourceUnavailable.class);
        assertThat(knowledgeChunkStore.storeInvocations).isZero();
    }

    private static final class CountingKnowledgeEmbedding implements KnowledgeEmbedding {

        private final EmbeddingPort delegate;
        private int documentEmbedCount;

        private CountingKnowledgeEmbedding(EmbeddingPort delegate) {
            this.delegate = delegate;
        }

        private int documentEmbedCount() {
            return documentEmbedCount;
        }

        @Override
        public EmbeddingResult embedDocument(KnowledgeEmbedding.DocumentCommand command) {
            documentEmbedCount++;
            return delegate.embedDocument(command.text());
        }

        @Override
        public EmbeddingResult embedQuery(KnowledgeEmbedding.QueryCommand command) {
            return delegate.embedQuery(command.text());
        }
    }

    private static final class SourceUnavailableKnowledgeEmbedding implements KnowledgeEmbedding {

        @Override
        public EmbeddingResult embedDocument(KnowledgeEmbedding.DocumentCommand command) {
            return new EmbeddingResult.SourceUnavailable(
                    "Ollama embeddings", "embedding service unavailable", "start Ollama");
        }

        @Override
        public EmbeddingResult embedQuery(KnowledgeEmbedding.QueryCommand command) {
            return embedDocument(new KnowledgeEmbedding.DocumentCommand(command.text()));
        }
    }

    private static final class StubProductPageSource implements ProductPageSource {

        private String text;
        private ProductPageResult unavailable;

        @Override
        public ProductPageResult fetchAndExtract(ProductPageSource.Command command) {
            if (unavailable != null) {
                return unavailable;
            }
            return new ProductPageResult.Success(text);
        }
    }

    private static final class StubKnowledgeChunkStore implements KnowledgeChunkStore {

        private final Map<String, String> hashesBySource = new HashMap<>();
        private final Map<String, List<RagChunk>> chunksBySource = new HashMap<>();
        private int storeInvocations;
        private List<RagChunk> storedChunks = List.of();

        @Override
        public List<KnowledgeSimilarityMatch> findSimilar(KnowledgeChunkStore.FindSimilarCommand command) {
            throw new UnsupportedOperationException("findSimilar is not used in ingestion tests");
        }

        @Override
        public ChunkStorageOutcome storeChunks(KnowledgeChunkStore.StoreChunksCommand command) {
            boolean replacing = hashesBySource.containsKey(command.sourceUrl());
            storeInvocations++;
            storedChunks = List.copyOf(command.chunks());
            hashesBySource.put(command.sourceUrl(), command.contentHash());
            chunksBySource.put(command.sourceUrl(), List.copyOf(command.chunks()));
            RagIngestionReport.Outcome outcome =
                    replacing
                            ? RagIngestionReport.Outcome.REPLACED
                            : RagIngestionReport.Outcome.INGESTED;
            return new ChunkStorageOutcome.Stored(outcome);
        }

        @Override
        public StoredSourceState findContentHashForSource(KnowledgeChunkStore.FindContentHashCommand command) {
            String hash = hashesBySource.get(command.sourceUrl());
            if (hash == null) {
                return new StoredSourceState.Absent();
            }
            return new StoredSourceState.Stored(
                    hash, chunksBySource.getOrDefault(command.sourceUrl(), List.of()).size());
        }
    }
}
