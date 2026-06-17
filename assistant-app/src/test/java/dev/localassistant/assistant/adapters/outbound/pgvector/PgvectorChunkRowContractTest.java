package dev.localassistant.assistant.adapters.outbound.pgvector;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PgvectorChunkRowContractTest {

    @Test
    void columnNamesMatchRagChunkFieldMapping() {
        assertThat(PgvectorChunkColumns.CHUNK_TEXT).isEqualTo("chunk_text");
        assertThat(PgvectorChunkColumns.EMBEDDING).isEqualTo("embedding");
        assertThat(PgvectorChunkColumns.SOURCE_URL).isEqualTo("source_url");
        assertThat(PgvectorChunkColumns.CONTENT_HASH).isEqualTo("content_hash");
        assertThat(PgvectorChunkColumns.CHUNK_INDEX).isEqualTo("chunk_index");
        assertThat(PgvectorChunkColumns.INGESTED_AT).isEqualTo("ingested_at");
    }

    @Test
    void insertStatementListsExpectedColumnsInOrder() {
        String insertColumns =
                String.join(
                        ", ",
                        PgvectorChunkColumns.CHUNK_TEXT,
                        PgvectorChunkColumns.EMBEDDING,
                        PgvectorChunkColumns.SOURCE_URL,
                        PgvectorChunkColumns.CONTENT_HASH,
                        PgvectorChunkColumns.CHUNK_INDEX,
                        PgvectorChunkColumns.INGESTED_AT);

        assertThat(insertColumns)
                .isEqualTo(
                        "chunk_text, embedding, source_url, content_hash, chunk_index, ingested_at");
    }

    @Test
    void embeddingColumnUsesVector768TypeInSchemaResource() throws Exception {
        String schema =
                new String(
                        getClass()
                                .getClassLoader()
                                .getResourceAsStream("db/rag-schema.sql")
                                .readAllBytes());

        assertThat(schema).contains("embedding vector(768)");
        assertThat(schema).contains("idx_rag_chunks_source_url_content_hash");
        assertThat(schema).contains("vector_cosine_ops");
    }
}
