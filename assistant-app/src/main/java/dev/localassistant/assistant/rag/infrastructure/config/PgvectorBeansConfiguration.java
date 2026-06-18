package dev.localassistant.assistant.rag.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

import dev.localassistant.assistant.rag.infrastructure.PgvectorIngestionRepository;
import dev.localassistant.assistant.rag.infrastructure.PgvectorSchemaInitializer;

/**
 * Profile-neutral pgvector JDBC stack. Registered via {@code @Import} from production or test
 * configuration only — not component-scanned.
 */
public class PgvectorBeansConfiguration {

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
    @Primary
    @ConditionalOnBean(PgvectorIngestionRepository.class)
    PgvectorSchemaInitializer initializedPgvectorSchema(
            PgvectorIngestionRepository pgvectorIngestionRepository,
            PgvectorSchemaInitializer pgvectorSchemaInitializer) {
        pgvectorSchemaInitializer.initializeSchema();
        return pgvectorSchemaInitializer;
    }
}
