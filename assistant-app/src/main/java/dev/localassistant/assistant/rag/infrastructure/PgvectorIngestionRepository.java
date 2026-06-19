package dev.localassistant.assistant.rag.infrastructure;

import dev.localassistant.assistant.rag.domain.RagChunk;
import dev.localassistant.assistant.rag.domain.StoredSourceState;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;

public final class PgvectorIngestionRepository {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public PgvectorIngestionRepository(final JdbcTemplate jdbcTemplate, final TransactionTemplate transactionTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate, "transactionTemplate");
    }

    int replaceChunksForSource(final String sourceUrl, final String contentHash, final List<RagChunk> chunks) {
        for (final RagChunk chunk : chunks) {
            if (!sourceUrl.equals(chunk.sourceUrl())) {
                throw new IllegalArgumentException(
                    "All chunks must have sourceUrl matching the sourceUrl parameter");
            }
        }

        final var deletedRows =
            transactionTemplate.execute(
                status -> {
                    final var deleted =
                        jdbcTemplate.update(
                            "DELETE FROM "
                                + PgvectorChunkColumns.RAG_CHUNKS_TABLE
                                + " WHERE "
                                + PgvectorChunkColumns.SOURCE_URL
                                + " = ?",
                            sourceUrl);
                    insertChunks(sourceUrl, contentHash, chunks);
                    return deleted;
                });
        return deletedRows == null ? 0 : deletedRows;
    }

    private void insertChunks(final String sourceUrl, final String contentHash, final List<RagChunk> chunks) {
        final var insertSql =
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

        jdbcTemplate.batchUpdate(
            insertSql,
            new BatchPreparedStatementSetter() {
                @Override
                public void setValues(final PreparedStatement statement, final int index) throws SQLException {
                    final var chunk = chunks.get(index);
                    statement.setString(1, chunk.chunkText());
                    statement.setString(2, PgvectorEmbeddingCodec.toVectorLiteral(chunk.embedding()));
                    statement.setString(3, sourceUrl);
                    statement.setString(4, contentHash);
                    statement.setInt(5, chunk.chunkIndex());
                    statement.setTimestamp(6, Timestamp.from(chunk.ingestionTimestamp()));
                }

                @Override
                public int getBatchSize() {
                    return chunks.size();
                }
            });
    }

    StoredSourceState findStoredSourceState(final String sourceUrl) {
        final var stored =
            jdbcTemplate.query(
                "SELECT "
                    + PgvectorChunkColumns.CONTENT_HASH
                    + ", COUNT(*) AS "
                    + PgvectorChunkColumns.CHUNK_COUNT_ALIAS
                    + " FROM "
                    + PgvectorChunkColumns.RAG_CHUNKS_TABLE
                    + " WHERE "
                    + PgvectorChunkColumns.SOURCE_URL
                    + " = ? GROUP BY "
                    + PgvectorChunkColumns.CONTENT_HASH
                    + " LIMIT 1",
                (resultSet, rowNum) ->
                    new StoredSourceState.Stored(
                        resultSet.getString(PgvectorChunkColumns.CONTENT_HASH),
                        resultSet.getInt(PgvectorChunkColumns.CHUNK_COUNT_ALIAS)),
                sourceUrl);
        return stored.isEmpty() ? new StoredSourceState.Absent() : stored.getFirst();
    }

    // pgvector's `<=>` is cosine distance in [0, 2]; cosine similarity is `1 - distance`. The same
    // expression is reused for the projected score and the threshold filter so both rank on one metric.
    private static final String COSINE_SIMILARITY_EXPRESSION =
        "1 - (" + PgvectorChunkColumns.EMBEDDING + " <=> ?::vector)";
    private static final String COSINE_DISTANCE_EXPRESSION =
        PgvectorChunkColumns.EMBEDDING + " <=> ?::vector";

    List<PgvectorSimilarityMatch> findSimilarChunks(final float[] queryEmbedding, final int topK, final double minSimilarity) {
        final var vectorLiteral = PgvectorEmbeddingCodec.toVectorLiteral(queryEmbedding);
        final var sql =
            "SELECT "
                + PgvectorChunkColumns.CHUNK_TEXT
                + ", "
                + PgvectorChunkColumns.SOURCE_URL
                + ", "
                + PgvectorChunkColumns.CONTENT_HASH
                + ", "
                + PgvectorChunkColumns.CHUNK_INDEX
                + ", "
                + COSINE_SIMILARITY_EXPRESSION
                + " AS "
                + PgvectorChunkColumns.SIMILARITY_ALIAS
                + " FROM "
                + PgvectorChunkColumns.RAG_CHUNKS_TABLE
                + " WHERE "
                + COSINE_SIMILARITY_EXPRESSION
                + " >= ? ORDER BY "
                + COSINE_DISTANCE_EXPRESSION
                + " LIMIT ?";

        return jdbcTemplate.query(
            sql,
            (resultSet, rowNum) ->
                new PgvectorSimilarityMatch(
                    resultSet.getString(PgvectorChunkColumns.CHUNK_TEXT),
                    resultSet.getString(PgvectorChunkColumns.SOURCE_URL),
                    resultSet.getString(PgvectorChunkColumns.CONTENT_HASH),
                    resultSet.getInt(PgvectorChunkColumns.CHUNK_INDEX),
                    resultSet.getDouble(PgvectorChunkColumns.SIMILARITY_ALIAS)),
            vectorLiteral,
            vectorLiteral,
            minSimilarity,
            vectorLiteral,
            topK);
    }
}
