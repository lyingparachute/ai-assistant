package dev.localassistant.assistant.adapters.outbound.pgvector;

import dev.localassistant.assistant.adapters.outbound.mcp.support.McpTestConfiguration;
import dev.localassistant.assistant.adapters.outbound.pgvector.support.DeterministicTestEmbeddingAdapter;
import dev.localassistant.assistant.llm.EmbeddingPort;
import dev.localassistant.assistant.rag.KnowledgeSnippet;
import dev.localassistant.assistant.rag.RagChunk;
import dev.localassistant.assistant.rag.RagIngestionReport;
import dev.localassistant.assistant.rag.RagIngestionResult;
import dev.localassistant.assistant.rag.RagKnowledgePort;
import dev.localassistant.assistant.rag.RagRetrievalPolicy;
import dev.localassistant.assistant.rag.RagRetrievalResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import({McpTestConfiguration.class, PgvectorTestConfiguration.class})
@Testcontainers
class PgvectorRagAdapterIntegrationTest {

    private static final DockerImageName PGVECTOR_IMAGE =
            DockerImageName.parse("pgvector/pgvector:pg17");

    private static final String SOURCE_URL = "https://example.test/cdq-fraud-guard";
    private static final String FIRST_HASH = "hash-v1";
    private static final String SECOND_HASH = "hash-v2";
    private static final Instant INGESTED_AT = Instant.parse("2026-06-15T12:00:00Z");

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PGVECTOR_IMAGE)
            .withDatabaseName("assistant_rag_test")
            .withUsername("assistant")
            .withPassword("assistant");

    @DynamicPropertySource
    static void registerPgvectorProperties(DynamicPropertyRegistry registry) {
        registry.add("assistant.rag.jdbc-url", postgres::getJdbcUrl);
        registry.add("assistant.rag.username", postgres::getUsername);
        registry.add("assistant.rag.password", postgres::getPassword);
        registry.add("assistant.rag.relevance-threshold", () -> "0.3");
    }

    @Autowired
    private RagKnowledgePort ragKnowledgePort;

    @Autowired
    private PgvectorRagAdapter pgvectorRagAdapter;

    @Autowired
    private JdbcTemplate ragJdbcTemplate;

    @Autowired
    private EmbeddingPort embeddingPort;

    private DeterministicTestEmbeddingAdapter deterministicEmbeddingAdapter;

    @BeforeEach
    void resetDatabase() {
        deterministicEmbeddingAdapter = (DeterministicTestEmbeddingAdapter) embeddingPort;
        ragJdbcTemplate.execute("TRUNCATE rag_chunks RESTART IDENTITY");
        pgvectorRagAdapter.initializeSchema();
    }

    @Test
    void schemaInitializationCreatesRagChunksTable() {
        Integer tableCount =
                ragJdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM information_schema.tables
                        WHERE table_schema = 'public' AND table_name = 'rag_chunks'
                        """,
                        Integer.class);

        assertThat(tableCount).isEqualTo(1);
    }

    @Test
    void insertChunksStoresVector768Embeddings() {
        RagIngestionResult result = ingestSingleChunk("Fraud Guard monitors suspicious transactions.");

        assertThat(result).isInstanceOf(RagIngestionResult.Success.class);
        RagIngestionResult.Success success = (RagIngestionResult.Success) result;
        assertThat(success.report().outcome()).isEqualTo(RagIngestionReport.Outcome.INGESTED);
        assertThat(success.report().chunkCount()).isEqualTo(1);

        Integer dimension =
                ragJdbcTemplate.queryForObject(
                        """
                        SELECT vector_dims(embedding)
                        FROM rag_chunks
                        WHERE source_url = ?
                        """,
                        Integer.class,
                        SOURCE_URL);

        assertThat(dimension).isEqualTo(768);
    }

    @Test
    void similarityRetrievalReturnsExpectedSnippetForRelevantQuery() {
        ingestSingleChunk("Fraud Guard monitors suspicious transactions for payment fraud.");

        RagRetrievalResult retrievalResult =
                ragKnowledgePort.retrieve(
                        "How does Fraud Guard monitor suspicious payment fraud?",
                        new RagRetrievalPolicy(3, 0.3));

        assertThat(retrievalResult).isInstanceOf(RagRetrievalResult.Success.class);
        RagRetrievalResult.Success success = (RagRetrievalResult.Success) retrievalResult;
        KnowledgeSnippet snippet = success.snippets().getFirst();
        assertThat(snippet.chunkText()).contains("Fraud Guard monitors suspicious transactions");
        assertThat(snippet.similarityScore()).isPresent();
        assertThat(snippet.similarityScore().orElseThrow()).isGreaterThanOrEqualTo(0.3);
    }

    @Test
    void storedChunkMetadataRoundTripsThroughSimilarityRetrieval() {
        String chunkText = "Fraud Guard monitors suspicious transactions for payment fraud.";
        String contentHash = "metadata-round-trip-hash";
        RagIngestionResult result =
                ingestChunks(
                        contentHash,
                        List.of(chunk(0, chunkText, chunkText, contentHash)));
        assertThat(result).isInstanceOf(RagIngestionResult.Success.class);

        RagRetrievalResult retrievalResult =
                ragKnowledgePort.retrieve(
                        "How does Fraud Guard monitor suspicious payment fraud?",
                        new RagRetrievalPolicy(3, 0.3));

        assertThat(retrievalResult).isInstanceOf(RagRetrievalResult.Success.class);
        KnowledgeSnippet snippet = ((RagRetrievalResult.Success) retrievalResult).snippets().getFirst();
        assertThat(snippet.chunkText()).isEqualTo(chunkText);
        assertThat(snippet.sourceUrl()).isEqualTo(SOURCE_URL);
        assertThat(snippet.contentHash()).isEqualTo(contentHash);
        assertThat(snippet.chunkIndex()).isZero();
        assertThat(snippet.similarityScore()).isPresent();
    }

    @Test
    void reIngestionReplacesPriorChunkSetWithoutDuplicates() {
        RagIngestionResult firstIngestion =
                ingestChunks(
                        FIRST_HASH,
                        List.of(
                                chunk(
                                        0,
                                        "First chunk about fraud detection rules.",
                                        "First chunk about fraud detection rules.",
                                        FIRST_HASH)));
        assertThat(firstIngestion).isInstanceOf(RagIngestionResult.Success.class);
        assertThat(((RagIngestionResult.Success) firstIngestion).report().outcome())
                .isEqualTo(RagIngestionReport.Outcome.INGESTED);

        RagIngestionResult secondIngestion =
                ingestChunks(
                        SECOND_HASH,
                        List.of(
                                chunk(
                                        0,
                                        "Replacement chunk about chargeback prevention.",
                                        "Replacement chunk about chargeback prevention.",
                                        SECOND_HASH),
                                chunk(
                                        1,
                                        "Second replacement chunk about merchant risk scoring.",
                                        "Second replacement chunk about merchant risk scoring.",
                                        SECOND_HASH)));
        assertThat(secondIngestion).isInstanceOf(RagIngestionResult.Success.class);
        RagIngestionResult.Success replaced = (RagIngestionResult.Success) secondIngestion;
        assertThat(replaced.report().outcome()).isEqualTo(RagIngestionReport.Outcome.REPLACED);
        assertThat(replaced.report().chunkCount()).isEqualTo(2);

        Integer storedCount =
                ragJdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM rag_chunks WHERE source_url = ?",
                        Integer.class,
                        SOURCE_URL);
        assertThat(storedCount).isEqualTo(2);

        List<String> chunkTexts =
                ragJdbcTemplate.query(
                        "SELECT chunk_text FROM rag_chunks WHERE source_url = ? ORDER BY chunk_index",
                        (resultSet, rowNum) -> resultSet.getString("chunk_text"),
                        SOURCE_URL);
        assertThat(chunkTexts)
                .containsExactly(
                        "Replacement chunk about chargeback prevention.",
                        "Second replacement chunk about merchant risk scoring.");
        assertThat(chunkTexts).doesNotContain("First chunk about fraud detection rules.");
    }

    private RagIngestionResult ingestSingleChunk(String chunkText) {
        return ingestChunks(FIRST_HASH, List.of(chunk(0, chunkText, chunkText, FIRST_HASH)));
    }

    private RagIngestionResult ingestChunks(String contentHash, List<RagChunk> chunks) {
        return ragKnowledgePort.storeChunks(SOURCE_URL, contentHash, chunks);
    }

    private RagChunk chunk(int chunkIndex, String chunkText, String embeddingText, String contentHash) {
        float[] embedding =
                ((dev.localassistant.assistant.llm.EmbeddingResult.Success)
                                deterministicEmbeddingAdapter.embedDocument(embeddingText))
                        .embedding();
        return new RagChunk(chunkText, embedding, SOURCE_URL, contentHash, chunkIndex, INGESTED_AT);
    }
}
