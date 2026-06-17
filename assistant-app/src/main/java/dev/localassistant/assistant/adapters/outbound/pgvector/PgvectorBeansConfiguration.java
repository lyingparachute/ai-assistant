package dev.localassistant.assistant.adapters.outbound.pgvector;

import dev.localassistant.assistant.llm.EmbeddingPort;
import dev.localassistant.assistant.rag.RagKnowledgePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

/**
 * Profile-neutral pgvector bean graph shared by the production {@code PgvectorOutboundConfiguration}
 * and the test configuration. Registered only via {@code @Import}; intentionally not annotated with
 * {@code @Configuration} so component scanning ignores it and the importing configuration owns the
 * profile and conditional guards. The {@code ragDataSource} bean is supplied by the importing
 * configuration so production and tests own their own pool tuning. Beans run in lite mode, so each
 * method receives its dependencies as parameters rather than calling sibling bean methods.
 */
class PgvectorBeansConfiguration {

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
    @Primary
    @ConditionalOnBean(EmbeddingPort.class)
    RagKnowledgePort ragKnowledgePort(PgvectorRagAdapter pgvectorRagAdapter) {
        return pgvectorRagAdapter;
    }
}
