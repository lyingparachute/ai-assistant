package dev.localassistant.assistant.rag;

import dev.localassistant.assistant.adapters.outbound.mcp.support.McpTestConfiguration;
import dev.localassistant.assistant.adapters.outbound.pgvector.PgvectorTestConfiguration;
import dev.localassistant.assistant.rag.support.RagIngestionTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest
@ActiveProfiles("test")
@Import({McpTestConfiguration.class, PgvectorTestConfiguration.class, RagIngestionTestConfiguration.class})
@org.springframework.test.context.ContextConfiguration(
        initializers = dev.localassistant.assistant.support.ChatPathPortStubs.class)
@Testcontainers
class RagIngestionSourceUnavailableIntegrationTest {

    private static final DockerImageName PGVECTOR_IMAGE =
            DockerImageName.parse("pgvector/pgvector:pg17");

    private static final String SOURCE_URL = "https://example.test/cdq-fraud-guard";
    private static final String PGVECTOR_SOURCE_LABEL = "pgvector RAG";

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
    private RagIngestionUseCase ragIngestionUseCase;

    @Test
    void ingestReturnsSourceUnavailableWhenPgvectorIsDownInsteadOfThrowing() {
        postgres.stop();

        RagIngestionResult result = ragIngestionUseCase.ingest(SOURCE_URL);

        assertThat(result).isInstanceOf(RagIngestionResult.SourceUnavailable.class);
        RagIngestionResult.SourceUnavailable unavailable = (RagIngestionResult.SourceUnavailable) result;
        assertThat(unavailable.sourceLabel()).isEqualTo(PGVECTOR_SOURCE_LABEL);
        assertThatCode(() -> ragIngestionUseCase.ingest(SOURCE_URL)).doesNotThrowAnyException();
    }
}
