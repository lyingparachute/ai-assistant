package dev.localassistant.assistant.adapters.outbound.pgvector;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.localassistant.assistant.config.AssistantRagStorageProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

@Configuration
@Profile("!test")
@Import(PgvectorBeansConfiguration.class)
class PgvectorOutboundConfiguration {

    @Bean(destroyMethod = "close")
    DataSource ragDataSource(AssistantRagStorageProperties storageProperties) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(storageProperties.jdbcUrl());
        config.setUsername(storageProperties.username());
        config.setPassword(storageProperties.password());
        config.setPoolName("rag-pgvector");
        return new HikariDataSource(config);
    }
}
