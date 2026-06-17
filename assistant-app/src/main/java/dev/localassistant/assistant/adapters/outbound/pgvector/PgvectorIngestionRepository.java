package dev.localassistant.assistant.adapters.outbound.pgvector;

import dev.localassistant.assistant.rag.RagChunk;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

class PgvectorIngestionRepository {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    PgvectorIngestionRepository(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate, "transactionTemplate");
    }

    void initializeSchema() {
        new PgvectorSchemaInitializer(jdbcTemplate).initializeSchema();
    }

    void replaceChunksForSource(String sourceUrl, String contentHash, List<RagChunk> chunks) {
        for (RagChunk chunk : chunks) {
            if (!sourceUrl.equals(chunk.sourceUrl())) {
                throw new IllegalArgumentException(
                        "All chunks must have sourceUrl matching the sourceUrl parameter");
            }
        }

        transactionTemplate.executeWithoutResult(
                status -> {
                    jdbcTemplate.update(
                            "DELETE FROM "
                                    + PgvectorChunkColumns.RAG_CHUNKS_TABLE
                                    + " WHERE "
                                    + PgvectorChunkColumns.SOURCE_URL
                                    + " = ?",
                            sourceUrl);

                    String insertSql =
                            "INSERT INTO "
                                    + PgvectorChunkColumns.RAG_CHUNKS_TABLE
                                    + " ("
                                    + PgvectorChunkColumns.CHUNK_TEXT
                                    + ", "
                                    + PgvectorChunkColumns.EMBEDDING
                                    + ", "
                                    + PgvectorChunkColumns.SOURCE_URL
                                    + ", "
                                    + PgvectorChunkColumns.CONTENT_HASH
                                    + ", "
                                    + PgvectorChunkColumns.CHUNK_INDEX
                                    + ", "
                                    + PgvectorChunkColumns.INGESTED_AT
                                    + ") VALUES (?, ?::vector, ?, ?, ?, ?)";

                    for (RagChunk chunk : chunks) {
                        jdbcTemplate.update(
                                insertSql,
                                chunk.chunkText(),
                                PgvectorEmbeddingCodec.toVectorLiteral(chunk.embedding()),
                                sourceUrl,
                                contentHash,
                                chunk.chunkIndex(),
                                Timestamp.from(chunk.ingestionTimestamp()));
                    }
                });
    }

    int countChunksForSource(String sourceUrl) {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM "
                                + PgvectorChunkColumns.RAG_CHUNKS_TABLE
                                + " WHERE "
                                + PgvectorChunkColumns.SOURCE_URL
                                + " = ?",
                        Integer.class,
                        sourceUrl);
        return count == null ? 0 : count;
    }

    Optional<String> findContentHashForSource(String sourceUrl) {
        List<String> hashes =
                jdbcTemplate.query(
                        "SELECT DISTINCT "
                                + PgvectorChunkColumns.CONTENT_HASH
                                + " FROM "
                                + PgvectorChunkColumns.RAG_CHUNKS_TABLE
                                + " WHERE "
                                + PgvectorChunkColumns.SOURCE_URL
                                + " = ? LIMIT 1",
                        (resultSet, rowNum) -> resultSet.getString(PgvectorChunkColumns.CONTENT_HASH),
                        sourceUrl);
        return hashes.isEmpty() ? Optional.empty() : Optional.of(hashes.getFirst());
    }

    List<PgvectorSimilarityMatch> findSimilarChunks(float[] queryEmbedding, int topK, double minSimilarity) {
        String vectorLiteral = PgvectorEmbeddingCodec.toVectorLiteral(queryEmbedding);
        String sql =
                "SELECT "
                        + PgvectorChunkColumns.CHUNK_TEXT
                        + ", "
                        + PgvectorChunkColumns.SOURCE_URL
                        + ", "
                        + PgvectorChunkColumns.CONTENT_HASH
                        + ", "
                        + PgvectorChunkColumns.CHUNK_INDEX
                        + ", 1 - ("
                        + PgvectorChunkColumns.EMBEDDING
                        + " <=> ?::vector) AS similarity "
                        + "FROM "
                        + PgvectorChunkColumns.RAG_CHUNKS_TABLE
                        + " WHERE 1 - ("
                        + PgvectorChunkColumns.EMBEDDING
                        + " <=> ?::vector) >= ? "
                        + "ORDER BY "
                        + PgvectorChunkColumns.EMBEDDING
                        + " <=> ?::vector "
                        + "LIMIT ?";

        return jdbcTemplate.query(
                sql,
                (resultSet, rowNum) ->
                        new PgvectorSimilarityMatch(
                                resultSet.getString(PgvectorChunkColumns.CHUNK_TEXT),
                                resultSet.getString(PgvectorChunkColumns.SOURCE_URL),
                                resultSet.getString(PgvectorChunkColumns.CONTENT_HASH),
                                resultSet.getInt(PgvectorChunkColumns.CHUNK_INDEX),
                                resultSet.getDouble("similarity")),
                vectorLiteral,
                vectorLiteral,
                minSimilarity,
                vectorLiteral,
                topK);
    }
}
