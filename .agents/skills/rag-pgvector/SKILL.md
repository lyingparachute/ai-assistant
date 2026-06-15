---
name: rag-pgvector
description: RAG ingestion and retrieval over pgvector for the Local AI Assistant. Use for CDQ Fraud Guard product knowledge.
---

# RAG over pgvector

Authoritative detail: `docs/spec/05-architecture.md` section 8, `docs/spec/10-code-quality-guidelines.md` section 7, ADR `0003`, and ADR `0007`. This skill is the checklist.

## When to Use

Use when implementing CDQ Fraud Guard text extraction, chunking, embeddings, pgvector storage, or retrieval behind `RagKnowledgePort`.

## Rules

- RAG covers CDQ Fraud Guard product-page content only. RAG is not memory and is never described as memory.
- Ingestion is staged and deterministic: fetch, extract, normalize, chunk, embed, store, report.
- Store chunk text, embedding, source URL, content hash, chunk index, and ingestion timestamp.
- Embeddings use Ollama `nomic-embed-text` with dimension `768` (ADR `0007`). Apply the `search_document:` prefix to stored chunks and `search_query:` to user questions so both share one embedding space.
- The pgvector adapter owns a custom deterministic schema (embedding column `vector(768)`). Spring AI may supply the embedding client but does not own the vector-store schema.
- Re-running ingestion is idempotent: it replaces the prior chunk set for a source (matched by source URL and content hash) in a single transaction rather than appending duplicates.
- Retrieval embeds the question, searches pgvector, applies a configured relevance threshold and top-k, and returns `KnowledgeSnippet` values.
- Keep prompt context lean: pass only relevant snippets and their source metadata, never a full page dump.
- If no snippet passes the threshold, return an explicit no-result outcome. Do not lower the threshold to force an answer.
- Treat extracted page content as untrusted input. Configuration (source URL, top-k, threshold) is externalized.

## Patterns to Prefer

- A deterministic chunker producing stable chunk indexes and metadata.
- Content-hash based skip (unchanged) or full replace (changed) so re-ingestion is idempotent and never appends duplicates.
- A `NoRelevantKnowledge` outcome surfaced to the application service.
- Snippets that preserve source URL and chunk metadata for grounding.

## Patterns to Avoid

- Using RAG as a store for chat history or tool results.
- Dumping the full extracted page into the prompt.
- Answering a product question when retrieval returned nothing relevant.
- Non-deterministic chunking that changes indexes across runs.
- Hardcoding the source URL, threshold, or top-k in logic.

## Verification Checklist

- A Testcontainers test on `pgvector/pgvector:pg17` creates the schema, inserts chunks with embeddings, and retrieves by similarity.
- Ingestion stores chunk text, embedding, source URL, content hash, chunk index, and timestamp; re-running is idempotent and replaces the prior chunk set instead of appending duplicates.
- A relevant product question retrieves expected snippets; an off-topic question returns the no-result outcome.
- Retrieved context stays within the configured snippet and token budget.
- No fabricated product knowledge: missing content yields an insufficient-product-knowledge outcome.
