package dev.localassistant.assistant.rag;

import dev.localassistant.assistant.adapters.outbound.pgvector.support.DeterministicTestEmbeddingAdapter;
import dev.localassistant.assistant.llm.EmbeddingPort;
import dev.localassistant.assistant.llm.EmbeddingResult;
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

    private StubProductKnowledgePort productKnowledgePort;
    private StubRagKnowledgePort ragKnowledgePort;
    private CountingEmbeddingPort embeddingPort;
    private RagIngestionUseCase useCase;

    @BeforeEach
    void setUp() {
        productKnowledgePort = new StubProductKnowledgePort();
        ragKnowledgePort = new StubRagKnowledgePort();
        embeddingPort = new CountingEmbeddingPort(new DeterministicTestEmbeddingAdapter());
        useCase =
                new RagIngestionUseCase(
                        embeddingPort,
                        ragKnowledgePort,
                        productKnowledgePort,
                        new DeterministicTextChunker(80, 10),
                        Clock.fixed(INGESTED_AT, ZoneOffset.UTC));
    }

    @Test
    void ingestsChunksWhenSourceIsNew() {
        productKnowledgePort.text =
                "Fraud Guard monitors suspicious transactions and helps prevent chargebacks.";

        RagIngestionResult result = useCase.ingest(SOURCE_URL);

        assertThat(result).isInstanceOf(RagIngestionResult.Success.class);
        RagIngestionReport report = ((RagIngestionResult.Success) result).report();
        assertThat(report.outcome()).isEqualTo(RagIngestionReport.Outcome.INGESTED);
        assertThat(report.chunkCount()).isPositive();
        assertThat(ragKnowledgePort.storedChunks).hasSize(report.chunkCount());
    }

    @Test
    void skipsReEmbeddingWhenContentHashIsUnchanged() {
        productKnowledgePort.text =
                "Fraud Guard monitors suspicious transactions and helps prevent chargebacks.";
        RagIngestionResult firstIngestion = useCase.ingest(SOURCE_URL);
        RagIngestionReport firstReport = ((RagIngestionResult.Success) firstIngestion).report();
        int embedCallsAfterFirstIngestion = embeddingPort.documentEmbedCount();

        RagIngestionResult secondIngestion = useCase.ingest(SOURCE_URL);
        RagIngestionReport secondReport = ((RagIngestionResult.Success) secondIngestion).report();

        assertThat(secondReport.outcome()).isEqualTo(RagIngestionReport.Outcome.UNCHANGED);
        assertThat(secondReport.contentHash()).isEqualTo(firstReport.contentHash());
        assertThat(secondReport.chunkCount()).isEqualTo(firstReport.chunkCount());
        assertThat(ragKnowledgePort.storeInvocations).isEqualTo(1);
        assertThat(embedCallsAfterFirstIngestion).isEqualTo(firstReport.chunkCount());
        assertThat(embeddingPort.documentEmbedCount()).isEqualTo(embedCallsAfterFirstIngestion);
    }

    @Test
    void replacesStoredChunksWhenContentChanges() {
        productKnowledgePort.text = "First version of Fraud Guard product knowledge.";
        useCase.ingest(SOURCE_URL);

        productKnowledgePort.text = "Updated Fraud Guard product knowledge with new details.";
        RagIngestionResult result = useCase.ingest(SOURCE_URL);

        assertThat(result).isInstanceOf(RagIngestionResult.Success.class);
        assertThat(((RagIngestionResult.Success) result).report().outcome())
                .isEqualTo(RagIngestionReport.Outcome.REPLACED);
        assertThat(ragKnowledgePort.storeInvocations).isEqualTo(2);
    }

    @Test
    void surfacesProductPageSourceUnavailable() {
        productKnowledgePort.unavailable =
                new ProductPageResult.SourceUnavailable("CDQ product page", "fetch failed", "check URL");

        RagIngestionResult result = useCase.ingest(SOURCE_URL);

        assertThat(result).isInstanceOf(RagIngestionResult.SourceUnavailable.class);
    }

    @Test
    void surfacesEmbeddingSourceUnavailableDuringIngestion() {
        productKnowledgePort.text =
                "Fraud Guard monitors suspicious transactions and helps prevent chargebacks.";
        useCase =
                new RagIngestionUseCase(
                        new SourceUnavailableEmbeddingPort(),
                        ragKnowledgePort,
                        productKnowledgePort,
                        new DeterministicTextChunker(80, 10),
                        Clock.fixed(INGESTED_AT, ZoneOffset.UTC));

        RagIngestionResult result = useCase.ingest(SOURCE_URL);

        assertThat(result).isInstanceOf(RagIngestionResult.SourceUnavailable.class);
        assertThat(ragKnowledgePort.storeInvocations).isZero();
    }

    private static final class CountingEmbeddingPort implements EmbeddingPort {

        private final EmbeddingPort delegate;
        private int documentEmbedCount;

        private CountingEmbeddingPort(EmbeddingPort delegate) {
            this.delegate = delegate;
        }

        private int documentEmbedCount() {
            return documentEmbedCount;
        }

        @Override
        public EmbeddingResult embedDocument(String text) {
            documentEmbedCount++;
            return delegate.embedDocument(text);
        }

        @Override
        public EmbeddingResult embedQuery(String text) {
            return delegate.embedQuery(text);
        }
    }

    private static final class SourceUnavailableEmbeddingPort implements EmbeddingPort {

        @Override
        public EmbeddingResult embedDocument(String text) {
            return new EmbeddingResult.SourceUnavailable(
                    "Ollama embeddings", "embedding service unavailable", "start Ollama");
        }

        @Override
        public EmbeddingResult embedQuery(String text) {
            return embedDocument(text);
        }
    }

    private static final class StubProductKnowledgePort implements ProductKnowledgePort {

        private String text;
        private ProductPageResult unavailable;

        @Override
        public ProductPageResult fetchAndExtract(String sourceUrl) {
            if (unavailable != null) {
                return unavailable;
            }
            return new ProductPageResult.Success(text);
        }
    }

    private static final class StubRagKnowledgePort implements RagKnowledgePort {

        private final Map<String, String> hashesBySource = new HashMap<>();
        private final Map<String, List<RagChunk>> chunksBySource = new HashMap<>();
        private int storeInvocations;
        private List<RagChunk> storedChunks = List.of();

        @Override
        public RagRetrievalResult retrieve(String questionText, RagRetrievalPolicy policy) {
            throw new UnsupportedOperationException("retrieve is not used in ingestion tests");
        }

        @Override
        public ChunkStorageOutcome storeChunks(String sourceUrl, String contentHash, List<RagChunk> chunks) {
            boolean replacing = hashesBySource.containsKey(sourceUrl);
            storeInvocations++;
            storedChunks = List.copyOf(chunks);
            hashesBySource.put(sourceUrl, contentHash);
            chunksBySource.put(sourceUrl, List.copyOf(chunks));
            RagIngestionReport.Outcome outcome =
                    replacing
                            ? RagIngestionReport.Outcome.REPLACED
                            : RagIngestionReport.Outcome.INGESTED;
            return new ChunkStorageOutcome.Stored(outcome);
        }

        @Override
        public StoredSourceState findContentHashForSource(String sourceUrl) {
            String hash = hashesBySource.get(sourceUrl);
            if (hash == null) {
                return new StoredSourceState.Absent();
            }
            return new StoredSourceState.Stored(
                    hash, chunksBySource.getOrDefault(sourceUrl, List.of()).size());
        }
    }
}
