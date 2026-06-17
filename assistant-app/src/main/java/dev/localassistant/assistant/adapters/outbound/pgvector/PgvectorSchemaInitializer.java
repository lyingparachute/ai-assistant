package dev.localassistant.assistant.adapters.outbound.pgvector;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

class PgvectorSchemaInitializer {

    private static final String SCHEMA_RESOURCE = "db/rag-schema.sql";

    private final JdbcTemplate jdbcTemplate;

    PgvectorSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    void initializeSchema() {
        for (String statement : splitStatements(readSchemaSql())) {
            jdbcTemplate.execute(statement);
        }
    }

    private List<String> splitStatements(String schemaSql) {
        return Arrays.stream(schemaSql.split(";"))
                .map(String::trim)
                .filter(statement -> !statement.isEmpty())
                .toList();
    }

    private String readSchemaSql() {
        try {
            ClassPathResource resource = new ClassPathResource(SCHEMA_RESOURCE);
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new PgvectorStorageException("Failed to read pgvector schema resource", exception);
        }
    }
}
