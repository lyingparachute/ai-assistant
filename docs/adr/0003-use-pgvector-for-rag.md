# ADR 0003: Use pgvector for RAG

## Status

Accepted

## Context

The assistant must answer CDQ Fraud Guard product questions using RAG over ingested product-page content. The assignment requires PostgreSQL with pgvector and specifies the image `pgvector/pgvector:pg17`.

The repository should be locally reproducible and testable with a clean database.

## Decision

Use PostgreSQL with pgvector as the vector database for RAG knowledge.

Use `pgvector/pgvector:pg17` for local development and integration tests where a containerized database is appropriate.

RAG ingestion should be staged and observable: extract, normalize, chunk, embed, store, and report. Optional local artifacts may be written for debugging ingestion, but they must not become required runtime state unless documented.

The project's own pgvector outbound adapter owns the storage schema. It is a custom deterministic schema holding chunk text, the embedding (`vector(768)` per ADR `0007`), source URL, content hash, chunk index, and ingestion timestamp, with idempotent replace on re-ingestion. Spring AI (ADR `0001`) may be used for embedding and model clients, but it does not own or manage the vector-store schema; the project does not delegate the table layout to a Spring AI managed `VectorStore`. This keeps content-hash idempotent replace and the explicit no-result threshold under the project's control.

## Consequences

Benefits:

- Satisfies the assignment requirement.
- Supports local reproducibility with a standard container image.
- Keeps structured chunk metadata and embeddings in one local database.
- Enables Testcontainers-based verification of schema, storage, and retrieval behavior.
- Makes ingestion failures easier to diagnose through staged reports and chunk metadata.

Trade-offs:

- Requires Docker or another local container runtime.
- Requires schema setup and pgvector extension initialization.
- Retrieval quality depends on embedding model, chunking, top-k, and threshold configuration.
- PostgreSQL vector search is enough for this assignment, but may need tuning for larger corpora.

## Alternatives Considered

- In-memory vector store:
  - Reason rejected: does not satisfy the pgvector requirement and weakens reproducibility from a clean local database.
- File-based embeddings:
  - Reason rejected: harder to test realistic vector retrieval and metadata querying.
- Dedicated vector database:
  - Reason rejected: unnecessary extra infrastructure for a small local recruitment task.

## Verification

- RAG ingestion stores chunks, embeddings, source URL, content hash, and chunk index in pgvector.
- Ingestion report records source URL, content hash, chunk count, and skipped or replaced content.
- Testcontainers uses `pgvector/pgvector:pg17` for storage and retrieval tests.
- Clean local ingestion produces retrievable chunks.
- RAG no-result behavior returns an explicit insufficient-product-knowledge outcome.
