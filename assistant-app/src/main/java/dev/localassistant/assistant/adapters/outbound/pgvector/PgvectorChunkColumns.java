package dev.localassistant.assistant.adapters.outbound.pgvector;

final class PgvectorChunkColumns {

    static final String RAG_CHUNKS_TABLE = "rag_chunks";

    static final String CHUNK_TEXT = "chunk_text";
    static final String EMBEDDING = "embedding";
    static final String SOURCE_URL = "source_url";
    static final String CONTENT_HASH = "content_hash";
    static final String CHUNK_INDEX = "chunk_index";
    static final String INGESTED_AT = "ingested_at";

    static final String CHUNK_COUNT_ALIAS = "chunk_count";
    static final String SIMILARITY_ALIAS = "similarity";

    private PgvectorChunkColumns() {
    }
}
