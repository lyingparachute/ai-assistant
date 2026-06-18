package dev.localassistant.assistant.rag.infrastructure.config;

import dev.localassistant.assistant.rag.infrastructure.PgvectorIngestionRepository;
import dev.localassistant.assistant.rag.infrastructure.PgvectorSchemaInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

/**
 * Profile-neutral pgvector JDBC stack. Registered via {@code @Import} from production or test
 * configuration only — not component-scanned.
 */
class PgvectorBeansConfiguration {

    @Bean
    JdbcTemplate ragJdbcTemplate(final DataSource ragDataSource) {
        return new JdbcTemplate(ragDataSource);
    }

    @Bean
    PlatformTransactionManager ragTransactionManager(final DataSource ragDataSource) {
        return new DataSourceTransactionManager(ragDataSource);
    }

    @Bean
    TransactionTemplate ragTransactionTemplate(final PlatformTransactionManager ragTransactionManager) {
        return new TransactionTemplate(ragTransactionManager);
    }

    @Bean
    PgvectorIngestionRepository pgvectorIngestionRepository(
        final JdbcTemplate ragJdbcTemplate, final TransactionTemplate ragTransactionTemplate) {
        return new PgvectorIngestionRepository(ragJdbcTemplate, ragTransactionTemplate);
    }

    @Bean
    PgvectorSchemaInitializer pgvectorSchemaInitializer(final JdbcTemplate ragJdbcTemplate) {
        final var initializer = new PgvectorSchemaInitializer(ragJdbcTemplate);
        initializer.initializeSchema();
        return initializer;
    }
}
