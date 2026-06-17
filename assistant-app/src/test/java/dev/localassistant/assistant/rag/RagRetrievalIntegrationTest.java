package dev.localassistant.assistant.rag;

import dev.localassistant.assistant.adapters.outbound.mcp.support.McpTestConfiguration;
import dev.localassistant.assistant.adapters.outbound.pgvector.PgvectorSchemaInitializer;
import dev.localassistant.assistant.adapters.outbound.pgvector.PgvectorTestConfiguration;
import dev.localassistant.assistant.config.AssistantRagRetrievalProperties;
import dev.localassistant.assistant.rag.support.RagIngestionTestConfiguration;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import({McpTestConfiguration.class, PgvectorTestConfiguration.class, RagIngestionTestConfiguration.class})
@org.springframework.test.context.ContextConfiguration(
        initializers = dev.localassistant.assistant.support.ChatPathPortStubs.class)
@Testcontainers
class RagRetrievalIntegrationTest {

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
        registry.add("assistant.rag.chunk-max-size", () -> "500");
        registry.add("assistant.rag.chunk-overlap", () -> "50");
    }

    @Autowired
    private RagIngestionUseCase ragIngestionUseCase;

    @Autowired
    private RagKnowledgePort ragKnowledgePort;

    @Autowired
    private AssistantRagRetrievalProperties retrievalProperties;

    @Autowired
    private JdbcTemplate ragJdbcTemplate;

    @Autowired
    private PgvectorSchemaInitializer pgvectorSchemaInitializer;

    @BeforeEach
    void resetDatabase() {
        pgvectorSchemaInitializer.initializeSchema();
        ragJdbcTemplate.execute("TRUNCATE rag_chunks RESTART IDENTITY");
    }

    @Test
    void relevantCdqQuestionRetrievesFixtureKnowledgeSnippets() {
        RagIngestionResult ingestionResult = ragIngestionUseCase.ingest(SOURCE_URL);
        assertThat(ingestionResult).isInstanceOf(RagIngestionResult.Success.class);

        RagRetrievalPolicy policy =
                new RagRetrievalPolicy(
                        retrievalProperties.topK(), retrievalProperties.relevanceThreshold());
        RagRetrievalResult retrievalResult =
                ragKnowledgePort.retrieve(
                        "How does Fraud Guard analyze transaction patterns and flag suspicious behavior before losses occur?",
                        policy);

        assertThat(retrievalResult).isInstanceOf(RagRetrievalResult.Success.class);
        RagRetrievalResult.Success success = (RagRetrievalResult.Success) retrievalResult;
        assertThat(success.snippets()).isNotEmpty();

        String combinedSnippetText =
                success.snippets().stream()
                        .map(KnowledgeSnippet::chunkText)
                        .reduce("", (left, right) -> left + " " + right);
        assertThat(combinedSnippetText)
                .containsAnyOf(
                        "payment fraud",
                        "real-time transaction monitoring",
                        "suspicious behavior",
                        "prevent chargebacks");

        KnowledgeSnippet firstSnippet = success.snippets().getFirst();
        assertThat(firstSnippet.sourceUrl()).isEqualTo(SOURCE_URL);
        assertThat(firstSnippet.contentHash()).isNotBlank();
        assertThat(firstSnippet.chunkIndex()).isGreaterThanOrEqualTo(0);
        assertThat(firstSnippet.retrievalScore().value())
                .isGreaterThanOrEqualTo(retrievalProperties.relevanceThreshold());
    }

    @Test
    void offTopicQuestionReturnsNoRelevantKnowledgeWithoutLoweringThreshold() {
        RagIngestionResult ingestionResult = ragIngestionUseCase.ingest(SOURCE_URL);
        assertThat(ingestionResult).isInstanceOf(RagIngestionResult.Success.class);

        double configuredThreshold = retrievalProperties.relevanceThreshold();
        assertThat(configuredThreshold).isEqualTo(0.5);

        RagRetrievalPolicy policy =
                new RagRetrievalPolicy(retrievalProperties.topK(), configuredThreshold);
        RagRetrievalResult retrievalResult =
                ragKnowledgePort.retrieve("What is the weather in Munich?", policy);

        assertThat(retrievalResult).isInstanceOf(RagRetrievalResult.NoRelevantKnowledge.class);
        assertThat(retrievalResult).isNotInstanceOf(RagRetrievalResult.Success.class);
    }
}
