package dev.localassistant.assistant.adapters.outbound.pgvector;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.localassistant.assistant.adapters.outbound.pgvector.support.DeterministicTestEmbeddingAdapter;
import dev.localassistant.assistant.config.AssistantRagProperties;
import dev.localassistant.assistant.llm.EmbeddingPort;
import dev.localassistant.assistant.rag.RagKnowledgePort;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

@TestConfiguration
@EnableConfigurationProperties(AssistantRagProperties.class)
public class PgvectorTestConfiguration {

    @Bean(destroyMethod = "close")
    DataSource ragDataSource(AssistantRagProperties assistantRagProperties) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(assistantRagProperties.jdbcUrl());
        config.setUsername(assistantRagProperties.username());
        config.setPassword(assistantRagProperties.password());
        config.setPoolName("rag-pgvector-test");
        return new HikariDataSource(config);
    }

    @Bean
    JdbcTemplate ragJdbcTemplate(DataSource ragDataSource) {
        return new JdbcTemplate(ragDataSource);
    }

    @Bean
    PlatformTransactionManager ragTransactionManager(DataSource ragDataSource) {
        return new DataSourceTransactionManager(ragDataSource);
    }

    @Bean
    TransactionTemplate ragTransactionTemplate(PlatformTransactionManager ragTransactionManager) {
        return new TransactionTemplate(ragTransactionManager);
    }

    @Bean
    PgvectorIngestionRepository pgvectorIngestionRepository(
            JdbcTemplate ragJdbcTemplate, TransactionTemplate ragTransactionTemplate) {
        return new PgvectorIngestionRepository(ragJdbcTemplate, ragTransactionTemplate);
    }

    @Bean
    @Primary
    EmbeddingPort deterministicTestEmbeddingPort() {
        return new DeterministicTestEmbeddingAdapter();
    }

    @Bean
    PgvectorRagAdapter pgvectorRagAdapter(
            PgvectorIngestionRepository pgvectorIngestionRepository,
            EmbeddingPort deterministicTestEmbeddingPort,
            JdbcTemplate ragJdbcTemplate) {
        new PgvectorSchemaInitializer(ragJdbcTemplate).initializeSchema();
        return new PgvectorRagAdapter(pgvectorIngestionRepository, deterministicTestEmbeddingPort);
    }

    @Bean
    @Primary
    RagKnowledgePort ragKnowledgePort(PgvectorRagAdapter pgvectorRagAdapter) {
        return pgvectorRagAdapter;
    }
}
