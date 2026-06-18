package dev.localassistant.assistant.rag.infrastructure.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.localassistant.assistant.rag.domain.port.outbound.KnowledgeChunkStore;
import dev.localassistant.assistant.rag.infrastructure.PgvectorIngestionRepository;
import dev.localassistant.assistant.rag.infrastructure.PgvectorKnowledgeChunkStore;
import dev.localassistant.assistant.rag.infrastructure.support.TestEmbeddingPortConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import javax.sql.DataSource;

@TestConfiguration
@EnableConfigurationProperties(AssistantRagStorageProperties.class)
@Import({TestEmbeddingPortConfiguration.class, PgvectorBeansConfiguration.class})
public class PgvectorTestConfiguration {

    // Fail fast against a stopped/unreachable container so the source-unavailable integration test
    // surfaces the typed outcome in seconds instead of waiting out Hikari's 30s default.
    private static final long TEST_CONNECTION_TIMEOUT_MILLIS = 2000L;

    @Bean(destroyMethod = "close")
    DataSource ragDataSource(AssistantRagStorageProperties storageProperties) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(storageProperties.jdbcUrl());
        config.setUsername(storageProperties.username());
        config.setPassword(storageProperties.password());
        config.setPoolName("rag-pgvector-test");
        config.setConnectionTimeout(TEST_CONNECTION_TIMEOUT_MILLIS);
        return new HikariDataSource(config);
    }

    @Bean
    KnowledgeChunkStore knowledgeChunkStore(PgvectorIngestionRepository pgvectorIngestionRepository) {
        return new PgvectorKnowledgeChunkStore(pgvectorIngestionRepository);
    }
}
