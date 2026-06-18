package dev.localassistant.assistant.rag.infrastructure.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.localassistant.assistant.rag.domain.port.outbound.KnowledgeChunkStore;
import dev.localassistant.assistant.rag.infrastructure.PgvectorIngestionRepository;
import dev.localassistant.assistant.rag.infrastructure.PgvectorKnowledgeChunkStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

@Configuration
@Profile("!test")
@Import(PgvectorBeansConfiguration.class)
class PgvectorInfrastructureConfiguration {

    @Bean(destroyMethod = "close")
    DataSource ragDataSource(final AssistantRagStorageProperties storageProperties) {
        final var config = new HikariConfig();
        config.setJdbcUrl(storageProperties.jdbcUrl());
        config.setUsername(storageProperties.username());
        config.setPassword(storageProperties.password());
        config.setPoolName("rag-pgvector");
        return new HikariDataSource(config);
    }

    @Bean
    KnowledgeChunkStore knowledgeChunkStore(final PgvectorIngestionRepository pgvectorIngestionRepository) {
        return new PgvectorKnowledgeChunkStore(pgvectorIngestionRepository);
    }
}
