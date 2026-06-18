package dev.localassistant.assistant.rag;

import dev.localassistant.assistant.rag.domain.RagIngestionReport;
import dev.localassistant.assistant.rag.domain.RagIngestionResult;
import dev.localassistant.assistant.rag.domain.port.inbound.IngestRag;
import dev.localassistant.assistant.rag.infrastructure.FixtureProductPageSource;
import dev.localassistant.assistant.rag.infrastructure.PgvectorSchemaInitializer;
import dev.localassistant.assistant.rag.infrastructure.config.PgvectorTestConfiguration;
import dev.localassistant.assistant.rag.support.RagIngestionTestConfiguration;
import dev.localassistant.assistant.shared.mcp.support.McpTestConfiguration;
import dev.localassistant.assistant.support.ChatPathPortStubs;
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

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import({McpTestConfiguration.class, PgvectorTestConfiguration.class, RagIngestionTestConfiguration.class})
@org.springframework.test.context.ContextConfiguration(initializers = ChatPathPortStubs.class)
@Testcontainers
class RagIngestionUseCaseIntegrationTest {

    private static final DockerImageName PGVECTOR_IMAGE =
            DockerImageName.parse("pgvector/pgvector:pg17");

    private static final String SOURCE_URL = "https://example.test/cdq-fraud-guard";

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
        registry.add("assistant.rag.source-url", () -> SOURCE_URL);
        registry.add("assistant.rag.chunk-max-size", () -> "120");
        registry.add("assistant.rag.chunk-overlap", () -> "20");
    }

    @Autowired
    private IngestRag ingestRag;

    @Autowired
    private JdbcTemplate ragJdbcTemplate;

    @Autowired
    private AtomicReference<FixtureProductPageSource> fixtureProductPageSourceHolder;

    @Autowired
    private PgvectorSchemaInitializer pgvectorSchemaInitializer;

    @BeforeEach
    void resetDatabase() throws IOException {
        pgvectorSchemaInitializer.initializeSchema();
        ragJdbcTemplate.execute("TRUNCATE rag_chunks RESTART IDENTITY");
        fixtureProductPageSourceHolder.set(
                FixtureProductPageSource.fromClasspathHtml(RagIngestionTestConfiguration.FIXTURE_HTML));
    }

    @Test
    void fullIngestFromFixtureHtmlStoresRetrievableChunks() {
        RagIngestionResult result = ingestRag.execute(new IngestRag.Command(SOURCE_URL));

        assertThat(result).isInstanceOf(RagIngestionResult.Success.class);
        RagIngestionReport report = ((RagIngestionResult.Success) result).report();
        assertThat(report.outcome()).isEqualTo(RagIngestionReport.Outcome.INGESTED);
        assertThat(report.chunkCount()).isPositive();

        Integer storedCount =
                ragJdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM rag_chunks WHERE source_url = ?",
                        Integer.class,
                        SOURCE_URL);
        assertThat(storedCount).isEqualTo(report.chunkCount());
    }

    @Test
    void reIngestUnchangedContentSkipsReEmbedding() {
        RagIngestionResult firstResult = ingestRag.execute(new IngestRag.Command(SOURCE_URL));
        RagIngestionReport firstReport = ((RagIngestionResult.Success) firstResult).report();

        RagIngestionResult secondResult = ingestRag.execute(new IngestRag.Command(SOURCE_URL));
        RagIngestionReport secondReport = ((RagIngestionResult.Success) secondResult).report();

        assertThat(secondReport.outcome()).isEqualTo(RagIngestionReport.Outcome.UNCHANGED);
        assertThat(secondReport.contentHash()).isEqualTo(firstReport.contentHash());
        assertThat(secondReport.chunkCount()).isEqualTo(firstReport.chunkCount());

        Integer storedCount =
                ragJdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM rag_chunks WHERE source_url = ?",
                        Integer.class,
                        SOURCE_URL);
        assertThat(storedCount).isEqualTo(firstReport.chunkCount());
    }

    @Test
    void changedContentHashReplacesStoredChunks() throws IOException {
        RagIngestionResult firstResult = ingestRag.execute(new IngestRag.Command(SOURCE_URL));
        RagIngestionReport firstReport = ((RagIngestionResult.Success) firstResult).report();

        fixtureProductPageSourceHolder.set(
                FixtureProductPageSource.fromClasspathHtml(RagIngestionTestConfiguration.FIXTURE_HTML)
                        .withExtractedText(
                                "Updated Fraud Guard knowledge with new chargeback prevention details."));

        RagIngestionResult secondResult = ingestRag.execute(new IngestRag.Command(SOURCE_URL));
        RagIngestionReport secondReport = ((RagIngestionResult.Success) secondResult).report();

        assertThat(secondReport.outcome()).isEqualTo(RagIngestionReport.Outcome.REPLACED);
        assertThat(secondReport.contentHash()).isNotEqualTo(firstReport.contentHash());

        Integer storedCount =
                ragJdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM rag_chunks WHERE source_url = ?",
                        Integer.class,
                        SOURCE_URL);
        assertThat(storedCount).isEqualTo(secondReport.chunkCount());
    }
}
