# ExecPlan — RAG ingestion & port redesign

Status: draft — round-1 + round-2 critic reviewed; round-2 blockers resolved in the addendum below
Owner: TBD
Source: docs/reviews/2026-06-16-code-quality-audit.md (R-2..R-6, R-10..R-13, R-16..R-22)
Scope module: `assistant-app` — `rag` package, `adapters/outbound/pgvector`, `adapters/outbound/ollama`
(embedding), `adapters/inbound/cli`, and tests. **Hard dependency: refactor-1 must land first** — it introduces
the `SourceUnavailability` VO in `tools`; this plan adopts it in the rag/embedding result types and does NOT
re-introduce it (STOP-on-dependency rule) [R1: P2-9].

Round-1 resolutions marked [R1: Pn-x].

## Why now

A pgvector outage during the ingestion hash/count read throws an unchecked exception **out of**
`RagIngestionUseCase` instead of a typed `RagIngestionResult.SourceUnavailable` — an AGENTS §8/§4 honesty/
no-silent-failure violation. The same area splits outcome classification across two layers and runs a
non-atomic count+replace. Fixing the structure removes the defect.

## Decision table — the core move (locked) [R1: P2-1, P2-2, P2-3]

The round-1 critic correctly found that "two-method port + derive outcome from DELETE row-count + preserve
idempotency + skip re-embedding" cannot all hold, because UNCHANGED must be decided **before** embedding via a
stored-hash read. Locked resolution:

- **UNCHANGED**: `RagIngestionUseCase` reads the prior stored content hash from the port **before** chunking/
  embedding; if it equals the new hash, return UNCHANGED and **do not call `storeChunks`** (preserves the
  skip-re-embedding invariant and `reIngestUnchangedContentSkipsReEmbedding`).
- **INGESTED vs REPLACED**: derived from the in-transaction DELETE row count. **`PgvectorIngestionRepository`
  signature change (locked)**: `replaceChunksForSource(...)` switches from `executeWithoutResult` (void,
  discards the count) to `transactionTemplate.execute(...)` returning the pre-delete row count; the adapter maps
  count>0 → REPLACED, count==0 → INGESTED, inside the single transaction (kills the TOCTOU).
- **Port shape (corrected)**: `RagKnowledgePort` = **three** methods — `retrieve`, `storeChunks`,
  `findContentHashForSource` — **all returning typed results, none throwing**. Only `countChunksForSource` is
  removed (its job moves into the DELETE-count). The earlier "exactly two methods" goal was wrong and is
  withdrawn.

## Problem (verified against source)

1. **R-5 (HIGH)**: `findContentHashForSource` (adapter 107-116) and `countChunksForSource` (118-128) catch and
   **rethrow** `PgvectorStorageException`; `RagIngestionUseCase` (49,51) calls both with no try/catch → DB
   outage crashes the use case. `retrieve`/`storeChunks` correctly return typed `SourceUnavailable`.
2. **R-3/R-4/R-6**: `storeChunks` calls `countChunksForSource` (separate untransacted `queryForObject`) then
   `replaceChunksForSource`; the count read is outside the replace transaction (TOCTOU). Adapter imports +
   constructs `RagIngestionReport` (boundary leak).
3. **R-2 (HIGH)**: `KnowledgeSnippet` = 6-field record with `boolean hasRetrievalSimilarityScore` + sentinel
   `double` (0.0 absent), conditional range guard (33-41), two factories (incl. `fromStoredChunk` 44-47),
   derived `Optional<Double>`. Permits contradictory state.
4. **R-10/R-11**: similarity SQL repeats the `1 - (embedding <=> ?)` cosine expression in SELECT+WHERE and the
   `"similarity"` alias literal in **two** places (115, 134); `%.8f` in codec:18 unexplained. The 768-dimension
   *constant* is already reused in `OllamaEmbeddingAdapter` (42-49) — the duplication is the hand-rolled length
   **comparison**, not the literal [R1: P2-7].
5. **R-12/R-13**: `PgvectorRagAdapter.initializeSchema()` port-widening pass-through; `PgvectorTestConfiguration`
   copies production wiring.
6. **Lesser**: `RagIngestionMode` static overloads + untestable `System.getenv` (R-18); use-case manual
   downcasts (R-17) + clumsy Optional (R-16); missing tests (R-20 chunker overlap, R-21 retrieval
   source-unavailable, R-22 mock-restating prefix tests).

## Target state (decisions locked)

- Port = `retrieve`, `storeChunks`, `findContentHashForSource`, all typed (no throw); `countChunksForSource`
  removed; the two read methods no longer rethrow `PgvectorStorageException` — they catch `RuntimeException` and
  return the typed source-unavailable result (consistent with `retrieve`/`storeChunks`).
- Outcome classification entirely in `RagIngestionUseCase`; adapter stops importing `RagIngestionReport`.
  `replaceChunksForSource` returns the deleted-row count; INGESTED/REPLACED derived from it in one transaction.
- **`KnowledgeSnippet` (locked) [R1: P2-6]**: first confirm `fromStoredChunk` has a production caller via grep.
  If it has **no** production caller (only tests construct stored snippets), delete the stored shape entirely and
  give `KnowledgeSnippet` a mandatory `RetrievalScore` value object (no flag, no sentinel, no Optional). If it
  has a live caller, split into sealed `StoredSnippet`/`RetrievedSnippet`. Default expectation: stored shape is
  dead → single record + mandatory `RetrievalScore`. The single-record-`OptionalDouble` option is rejected (it
  still permits a stored snippet to carry a score).
- One dimension check via `EmbeddingDimensions.matches(float[])` predicate in the embedding adapter.
- pgvector SQL: alias named constant in `PgvectorChunkColumns`; cosine distance→similarity expression extracted
  once with an explaining comment; `%.8f` a named justified constant.
- Schema init only via `PgvectorSchemaInitializer`; adapter pass-through removed; tests inject the initializer.
- **`PgvectorTestConfiguration` (locked) [R1: P2-10]**: production `PgvectorOutboundConfiguration` is
  `@Profile("!test")` with `@ConditionalOnBean(EmbeddingPort)` beans, so it cannot simply be imported under the
  `test` profile. Extract the production bean methods into a **profile-neutral** `@Configuration` base that both
  production (with its profile/conditional guards) and the test config import, overriding only the DataSource +
  a `@Primary` test `EmbeddingPort`. Note interaction with refactor-5's scan-filter fix — but this plan does not
  depend on it (the test config is explicitly imported, not scanned).
- **`RagIngestionMode` (locked) [R1: P2-11]**: collapse to one package-private
  `static boolean enabled(ApplicationArguments args, Function<String,String> env)`; public overloads delegate
  with `System::getenv`; `main` builds `ApplicationArguments` once. The env path becomes testable via a fake
  env function; `main`'s call is unchanged.
- Use-case pattern-matching switches (no downcasts); clean Optional idiom (R-16, if not already done by FIX-NOW
  — verify: FIX-NOW left R-16 to this plan).
- Tests: chunker multi-chunk overlap + exact-multiple boundary (R-20); retrieval `SourceUnavailable` (embedding
  fail + query throw) (R-21); env-enabled mode (R-18); embedding prefix tests assert returned vector not
  `verify(...)` (R-22).

## Non-goals

- No change to chunking algorithm, embedding model, distance metric, or ranking — only representation + error
  contract.
- No schema/stored-data change.
- `SourceUnavailability` VO ownership is refactor-1's; consumed here.

## Invariants to preserve

- Retrieval grounds only on CDQ product-page content; "insufficient product knowledge" stays typed; RAG never
  called memory/context (AGENTS §8).
- Idempotency exact: re-ingest identical → UNCHANGED **with no re-embedding** (guarded by
  `reIngestUnchangedContentSkipsReEmbedding`); changed → REPLACED; first → INGESTED.
- Testcontainers `pgvector/pgvector:pg17` (ADR 0003).
- No JDBC/pgvector/Spring AI types in `rag` package.

## Risks / open questions

- Ordered green sub-slices: (1) adopt `SourceUnavailability` + make both read methods typed (no throw); (2)
  `replaceChunksForSource` returns count + use-case owns outcome + atomic + drop `countChunksForSource`; (3)
  `KnowledgeSnippet` (after the `fromStoredChunk`-usage grep decides shape); (4) dimension predicate + SQL
  constants; (5) schema-init + profile-neutral test-config base; (6) `RagIngestionMode` seam + remaining tests.
- The idempotency integration test is the guard for slice 2; it must stay green and still assert no-re-embed.

## Definition of Done (binary)

- [ ] `RagKnowledgePort` declares exactly `retrieve`, `storeChunks`, `findContentHashForSource`; `grep -n
      countChunksForSource RagKnowledgePort.java` empty; no port method throws (all return a sealed result).
- [ ] **Real failing-DB test** [R1: P2-8]: a Testcontainers test that stops the pgvector container (or drops the
      `rag_chunks` table) then calls `RagIngestionUseCase.ingest`, asserting `RagIngestionResult.SourceUnavailable`
      with `sourceLabel == "pgvector RAG"` and that no exception escapes. (A stub-port return does NOT satisfy
      this bullet.)
- [ ] `grep -rn "RagIngestionReport" adapters/` empty (report built only in application layer).
- [ ] `replaceChunksForSource` returns the row count; `grep -n countChunksForSource adapters/` empty; the
      existence-check + replace are one `transactionTemplate.execute`.
- [ ] `KnowledgeSnippet` has no `boolean has*` and no sentinel double; score is mandatory `RetrievalScore`
      (or a sealed stored/retrieved split if `fromStoredChunk` is live — decision recorded in the slice).
- [ ] `grep -n "\.length !=\|\.length ==" OllamaEmbeddingAdapter.java` empty; it calls
      `EmbeddingDimensions.matches`.
- [ ] `reIngestUnchangedContentSkipsReEmbedding` still green and still asserts no re-embedding.
- [ ] New tests present: chunker overlap + exact-multiple; retrieval SourceUnavailable (embedding fail + query
      throw); env-enabled `RagIngestionMode`; embedding prefix tests assert returned vector.
- [ ] `PgvectorTestConfiguration` imports a shared profile-neutral config (no duplicated bean graph).
- [ ] `./mvnw -o test` BUILD SUCCESS (Docker required).

## Round-2 critic resolutions (authoritative; supersede the body where in conflict)

- **[P2R2-A + P2R2-B, blocking] `findContentHashForSource` returns a 3-state sealed result carrying hash AND
  chunk count.** Dropping `countChunksForSource` orphaned the UNCHANGED report's `chunkCount`. Locked: the port's
  read method returns a sealed `StoredSourceState` = `Stored(String contentHash, int chunkCount)` | `Absent` |
  `Unavailable(SourceUnavailability)`. Use-case branches: `Unavailable` → return `RagIngestionResult.SourceUnavailable`
  (no embedding); `Stored` with equal hash → UNCHANGED report built from the returned `chunkCount`; `Stored`
  differing / `Absent` → proceed to embed + `storeChunks`. This both preserves the no-re-embed invariant AND
  fixes R-5 (DB-down during the read is a typed outcome, never an uncaught throw, and never silently treated as
  "first ingestion").
- **[P2R2-C, blocking] DoD grep corrected.** `RagIngestionReport` is legitimately *read* by the inbound CLI
  adapter (`RagIngestionCommand`). The boundary rule is that the *outbound* adapter must not *construct* it.
  Replace the DoD bullet with: `grep -rn "new RagIngestionReport" assistant-app/src/main/java/.../adapters/outbound/`
  is empty.
- **[P2R2-D] Cross-package edges recorded.** Adopting `tools.SourceUnavailability` into `rag` and
  `llm.EmbeddingResult` adds edges `rag→tools`, `llm→tools`. Verified `tools` imports neither `rag` nor `llm`
  (leaf) → no cycle. Add this as an invariant + a DoD grep that `tools` imports no `rag`/`llm` types.
- **[P2R2-E] Scope adds `llm`** (`EmbeddingResult` adopts the VO). Slice 1 enumerates the three result types it
  changes: `RagIngestionResult`, `RagRetrievalResult`, `EmbeddingResult` (+ `ProductPageResult` if it carries the
  shape).
- **[P2R2-F] Cross-package test churn flagged.** `fromStoredChunk` has no production caller (grep-confirmed), so
  the stored shape is deleted and `KnowledgeSnippet` carries a mandatory `RetrievalScore`. Its only callers are
  `AnswerSourceTest` (question) and `ResponseComposerTest` (orchestration) — both must switch to the retrieval
  factory; add them to the slice-3 scope.
