Status: landed — 1b8ce32

# Phase 4: pgvector and RAG Ingestion

## Scope

- `assistant-app` RAG domain: `KnowledgeSnippet`, `RagKnowledgePort`, `RagRetrievalPolicy`, `RagIngestionUseCase`, explicit no-result and source-unavailable outcomes.
- `EmbeddingPort` in `llm` package; Ollama embedding outbound adapter via Spring AI (types confined to adapter/configuration).
- pgvector outbound adapter with custom `vector(768)` schema, JDBC storage, similarity retrieval.
- Product-page fetch/extract, normalize, deterministic chunk, embed, store pipeline for configured CDQ Fraud Guard URL.
- Ingestion report (source URL, content hash, chunk count, skipped/replaced outcome).
- Documented ingestion entry point (CLI / local inbound adapter) runnable without chat orchestration.
- Testcontainers on `pgvector/pgvector:pg17`: schema init, insert, similarity retrieval, idempotent re-ingestion.
- Contract tests for chunk row shape; fixture tests for relevant retrieval, off-topic no-result, extraction/embedding source-unavailable.
- README RAG setup: Docker/pgvector, `ollama pull nomic-embed-text`, ingestion command.

## Out of scope

- Phase 5 orchestration, `LlmPort`, `ResponseComposer`, deterministic source routing.
- Chat Interface / HTTP chat endpoint.
- Demo answer capture (Phase 7).
- Changes to `countries-mcp-server`.
- Committing this plan file.

## Definition of Done

- [x] `./mvnw -pl assistant-app test` passes; output recorded in `implementation-notes.md`.
- [x] Clean-database ingestion stores retrievable chunks with `vector(768)` embeddings and full metadata.
- [x] Re-running ingestion replaces prior chunk set without duplicates; unchanged content hash skips re-embedding or records unchanged outcome.
- [x] Representative CDQ fixture question retrieves expected `KnowledgeSnippet` values under configured top-k and threshold.
- [x] Off-topic question returns explicit insufficient-product-knowledge without lowering threshold.
- [x] CDQ extraction or embedding failures surface source-unavailable outcomes; tests do not fabricate product knowledge.
- [x] README documents Docker/pgvector setup, `ollama pull nomic-embed-text`, and ingestion command.

## Milestones

- [x] M1 — RAG domain types, `RagKnowledgePort`, `EmbeddingPort`, outcomes, unit tests
- [x] M2 — Maven deps, pgvector schema/repository, Testcontainers schema/insert/retrieve tests
- [x] M3 — Ollama embedding adapter, RAG configuration properties, controlled test embedding adapter
- [x] M4 — Ingestion pipeline, `RagIngestionUseCase`, CLI entry point, idempotent re-ingestion
- [x] M5 — Retrieval implementation, contract/fixture tests, README, verification

## Review loops

- Per milestone: implement → critic review → clean-code pass (max 3 loops).
