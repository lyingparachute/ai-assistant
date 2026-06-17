package dev.localassistant.assistant.adapters.outbound.pgvector;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.localassistant.assistant.config.AssistantRagProperties;
import dev.localassistant.assistant.llm.EmbeddingPort;
import dev.localassistant.assistant.rag.RagKnowledgePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

@Configuration
@Profile("!test")
@EnableConfigurationProperties(AssistantRagProperties.class)
class PgvectorOutboundConfiguration {

    @Bean(destroyMethod = "close")
    DataSource ragDataSource(AssistantRagProperties assistantRagProperties) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(assistantRagProperties.jdbcUrl());
        config.setUsername(assistantRagProperties.username());
        config.setPassword(assistantRagProperties.password());
        config.setPoolName("rag-pgvector");
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
    PgvectorSchemaInitializer pgvectorSchemaInitializer(JdbcTemplate ragJdbcTemplate) {
        return new PgvectorSchemaInitializer(ragJdbcTemplate);
    }

    @Bean
    @ConditionalOnBean(EmbeddingPort.class)
    PgvectorRagAdapter pgvectorRagAdapter(
            PgvectorIngestionRepository pgvectorIngestionRepository,
            EmbeddingPort embeddingPort,
            PgvectorSchemaInitializer pgvectorSchemaInitializer) {
        pgvectorSchemaInitializer.initializeSchema();
        return new PgvectorRagAdapter(pgvectorIngestionRepository, embeddingPort);
    }

    @Bean
    @ConditionalOnBean(EmbeddingPort.class)
    RagKnowledgePort ragKnowledgePort(PgvectorRagAdapter pgvectorRagAdapter) {
        return pgvectorRagAdapter;
    }
}
