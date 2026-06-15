# ADR 0007: Use Ollama nomic-embed-text Embedding Model

## Status

Accepted

## Context

RAG over CDQ Fraud Guard product-page content requires turning chunks and user
questions into embeddings (`docs/spec/00-assignment-analysis.md`, Phase 4 in
`docs/spec/06-implementation-plan.md`). ADR `0002` selects `qwen3:4b` for
language synthesis, but `qwen3:4b` is a chat model, not an embedding model, so a
separate embedding model is required.

The embedding model is a material decision: it fixes the pgvector vector column
dimension, the `ollama pull` step a reviewer must run, and retrieval quality.
The pgvector schema and migration in Phase 4 cannot be written until the
dimension is known.

The project is local-first and already uses Ollama (ADR `0002`) and Spring AI
(ADR `0001`), which provides an Ollama embedding client.

## Decision

Use the local Ollama embedding model `nomic-embed-text` (Nomic Embed v1.5) as the
configured default embedding model.

- Output dimension is `768`. The pgvector embedding column is `vector(768)`.
- Store full `768`-dimension vectors. Do not use Matryoshka truncation to a
  smaller dimension; the marginal storage saving is not worth the added
  configuration and re-ingestion risk for a single product page.
- The embedding model name is configurable, defaulting to `nomic-embed-text`,
  through the documented embedding configuration. The default must not be
  hardcoded in business logic.
- Embeddings are produced through `LlmPort` / the Ollama outbound adapter using
  Spring AI's Ollama embedding client. Spring AI types stay inside the adapter
  and configuration (ADR `0001`).

The chosen dimension was verified against the Ollama model page and the Nomic
`nomic-embed-text-v1.5` model card, which report a default output dimension of
`768`.

## Consequences

Benefits:

- Provides a dedicated, local, open embedding model that does not require a paid
  API or cloud hosting.
- Fixes the pgvector schema dimension at `vector(768)`, unblocking the Phase 4
  schema and migration.
- Integrates through the existing Ollama runtime and Spring AI embedding client.

Trade-offs:

- Reviewers must `ollama pull nomic-embed-text` in addition to `qwen3:4b`.
- Changing the embedding model later changes the vector dimension, which
  requires a schema migration and full re-ingestion. This must be called out if
  the model changes.
- Retrieval quality depends on this model plus chunking, top-k, and threshold
  configuration.

## Alternatives Considered

- `mxbai-embed-large` (1024-dimension):
  - Reason rejected: larger vectors and model footprint for no required quality
    gain on a single product page.
- `all-minilm` (384-dimension):
  - Reason rejected: weaker retrieval quality than `nomic-embed-text` for short
    and long context tasks.
- Reuse `qwen3:4b` for embeddings:
  - Reason rejected: `qwen3:4b` is a chat model, not an embedding model;
    embeddings from a chat model are not a supported or reliable path.

## Verification

- The pgvector schema defines the embedding column as `vector(768)`.
- Ingestion stores `768`-dimension embeddings; retrieval embeds the question with
  the same model and dimension.
- The embedding model name is externalized and defaults to `nomic-embed-text`,
  not hardcoded in business logic.
- A Testcontainers test on `pgvector/pgvector:pg17` inserts and retrieves
  `768`-dimension vectors.
- README documents pulling `nomic-embed-text` once implementation exists.
