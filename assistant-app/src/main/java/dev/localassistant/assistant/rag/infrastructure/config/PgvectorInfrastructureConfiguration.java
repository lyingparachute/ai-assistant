package dev.localassistant.assistant.rag.infrastructure.config;

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
public class PgvectorInfrastructureConfiguration {

    @Bean(destroyMethod = "close")
    DataSource ragDataSource(AssistantRagStorageProperties storageProperties) {
        var config = new HikariConfig();
        config.setJdbcUrl(storageProperties.jdbcUrl());
        config.setUsername(storageProperties.username());
        config.setPassword(storageProperties.password());
        config.setPoolName("rag-pgvector");
        return new HikariDataSource(config);
    }
}
