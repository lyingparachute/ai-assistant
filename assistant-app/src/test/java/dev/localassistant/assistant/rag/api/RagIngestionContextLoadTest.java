package dev.localassistant.assistant.rag.api;

import dev.localassistant.assistant.answering.api.http.ChatController;
import dev.localassistant.assistant.answering.domain.port.inbound.AnswerQuestion;
import dev.localassistant.assistant.rag.domain.port.inbound.IngestRag;
import dev.localassistant.assistant.rag.infrastructure.PgvectorSchemaInitializer;
import dev.localassistant.assistant.rag.infrastructure.config.PgvectorTestConfiguration;
import dev.localassistant.assistant.rag.support.RagIngestionTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"test", "ingest-rag"})
@Import({PgvectorTestConfiguration.class, RagIngestionTestConfiguration.class})
@Testcontainers
class RagIngestionContextLoadTest {

    private static final DockerImageName PGVECTOR_IMAGE =
            DockerImageName.parse("pgvector/pgvector:pg17");

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
    }

    @Autowired
    private ApplicationContext applicationContext;

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
    void ingestRagProfileLoadsIngestionWithoutChatOrchestration() {
        assertThat(applicationContext.getBeansOfType(ChatController.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(AnswerQuestion.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(IngestRag.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(RagIngestionCli.class)).hasSize(1);
    }
}
