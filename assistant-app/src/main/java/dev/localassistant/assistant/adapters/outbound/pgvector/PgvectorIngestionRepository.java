package dev.localassistant.assistant.adapters.outbound.pgvector;

import dev.localassistant.assistant.rag.RagChunk;
import dev.localassistant.assistant.rag.StoredSourceState;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;

class PgvectorIngestionRepository {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    PgvectorIngestionRepository(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate, "transactionTemplate");
    }

    int replaceChunksForSource(String sourceUrl, String contentHash, List<RagChunk> chunks) {
        for (RagChunk chunk : chunks) {
            if (!sourceUrl.equals(chunk.sourceUrl())) {
                throw new IllegalArgumentException(
                        "All chunks must have sourceUrl matching the sourceUrl parameter");
            }
        }

        Integer deletedRows =
                transactionTemplate.execute(
                        status -> {
                            int deleted =
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

    private void insertChunks(String sourceUrl, String contentHash, List<RagChunk> chunks) {
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
    }

    StoredSourceState findStoredSourceState(String sourceUrl) {
        List<StoredSourceState.Stored> stored =
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
