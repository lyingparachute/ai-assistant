# ExecPlan — Config records, properties consolidation & build/script hygiene

Status: draft — round-1 + round-2 critic reviewed; round-2 blockers resolved in the addendum below
Owner: TBD
Source: docs/reviews/2026-06-16-code-quality-audit.md (R-9, X-C-3, X-C-4, X-C-5, X-C-6, X-C-7, M-3, M-4, S-1, S-2, S-3)
Scope: `assistant-app` config package + resources; parent pom; `scripts/`; the dead MCP `working-directory`
property + its doc/yml sites. Independent of refactor-3a. Binding-sensitive — execute one properties type per
slice, each with a binding test, green before the next.

Round-1 resolutions marked [R1: Pn-x].

## Why now

The config layer mixes mutable JavaBean `@ConfigurationProperties` (record-style accessors without `get`,
non-idiomatic) with countries-mcp-server's validated records; duplicates the Ollama base URL and an
`@EnableConfigurationProperties` registration; enforces a cross-field invariant deep in a bean instead of at the
config boundary; ships a validated-but-inert `working-directory` property; and the launch scripts clone an
unpinned remote repo and hardcode a versioned jar name in two places. None large; together they are the
"config/build is messy and slightly dishonest about reproducibility" tail.

## Problem (verified against source)

1. **Records vs mutable beans (R-9, X-C-6, X-C-7)**: every assistant-app `@ConfigurationProperties` is a mutable
   setter bean with `countries()`/`weather()` accessors; countries-mcp-server uses validated records.
2. **`AssistantRagProperties` is `@EnableConfigurationProperties`-d in three configs** (Rag/Cdq/Pgvector)
   [R1: P3-1] — the other property types are already single-registered (verified); only the Rag type is
   triplicated.
3. **Ollama base URL duplicated** across `AssistantEmbeddingProperties` + `AssistantLlmProperties` defaults and
   twice in `application.yml` (51, 56) [R1: P3-6].
4. **Chunk invariant** `chunkOverlap < chunkMaxSize` enforced only in `DeterministicTextChunker:22-23`, not at
   the config boundary [R1: P3-8].
5. **Dead `working-directory` (M-4)**: validated/documented in `AssistantMcpProperties` but never applied (MCP
   SDK 1.0.0 `ServerParameters.Builder` has no cwd setter); referenced in docs/spec/12:87 AND docs/spec/13:81
   and four yml lines (application.yml 27,36; application-test.yml 17,26) [R1: P3-5].
6. **Scripts**: `scripts/mcp-weather:7` clones `github.com/semdin/mcp-weather` at unpinned HEAD + `npx tsx`
   (S-1); `capture-demo-answers.sh:12` uses `python3` only to JSON-encode (undeclared dep, S-2);
   `start-assistant.sh:10` + `application.yml:23` both hardcode `countries-mcp-server-0.1.0-SNAPSHOT.jar` (S-3).

## Target state (decisions locked) [R1: P3-4, P3-8]

- **assistant-app `@ConfigurationProperties` → validated constructor-bound records**, matching
  countries-mcp-server. Locked binding decisions:
  - Every current field default migrates to **`@DefaultValue`** on the record component (NOT Java field
    initializers — constructor binding ignores those).
  - `AssistantMcpProperties` nested `McpServer` becomes a record annotated `@NestedConfigurationProperty` where
    metadata is needed; its `List<String> args` / `Map<String,String> env` defensive copies move into the
    **compact constructor** (a canonical record component otherwise returns the stored reference).
  - The `AssistantRagProperties` split into storage/retrieval/chunking records **keeps the `assistant.rag.*`
    property prefixes** — no key rename (Invariant below). If any key must move, yml + docs update in the same
    change.
  - Each converted type gets a binding test asserting defaults **and** a populated round-trip (for
    `AssistantMcpProperties`, a populated nested `McpServer.args`/`env`).
- **One `@EnableConfigurationProperties(AssistantRagProperties...)` registration** (single owning config or a
  `@ConfigurationPropertiesScan`); adapters inject the bean. Other types already single — verify only.
- **Single Ollama base URL** [R1: P3-6]: one property `assistant.ollama.base-url`, referenced by both embedding
  and chat via Spring placeholder (`${assistant.ollama.base-url}`) in yml so the value literal appears once in
  yml AND once in Java. (Decision: placeholder over YAML anchor — Spring resolves `${}` natively;
  `grep -c 11434` ≤ 1 in both yml and Java.)
- **Chunk cross-field validation** [R1: P3-8]: an `@AssertTrue(message = "assistant.rag.chunk-overlap must be
  smaller than assistant.rag.chunk-max-size") boolean isOverlapSmallerThanMax()` on the chunking record, with
  `@Validated`. The redundant runtime guard in `DeterministicTextChunker` is **removed** (single validation layer
  at the boundary, per AGENTS §4); a binding test asserts a bad config fails context startup with that message.
- **`working-directory` removed** [R1: P3-5]: delete the field + validation + accessors/setter in
  `AssistantMcpProperties`, the two docs/spec sites (12:87, 13:81), and the four yml lines. Add a one-line note
  in docs/spec/12 that subprocess cwd is not configurable with the current MCP SDK, and document the **real**
  cwd assumption (the `../countries-mcp-server/...jar` relative path in args depends on `spring-boot:run`
  launching from `assistant-app/`).
- **Scripts**: pin `scripts/mcp-weather` to an exact commit SHA/tag with a comment marking it external untrusted
  input (S-1); replace `python3` JSON-encode with `jq` or guard via `require_command` (S-2); derive the
  countries jar path (resolve newest `countries-mcp-server-*.jar` or read the project version) so the version
  string is not pinned in two places (S-3).

## Non-goals

- No runtime behavior change for valid configuration (binding/validation/packaging only).
- No e2e/demo work (refactor-3a). No property-key renames without same-change yml + doc updates.

## Invariants to preserve

- No secrets/URLs/model names/ports/paths in production Java logic; documented defaults live in yml/scripts only
  (AGENTS §7; FIX-NOW already removed the DB credential defaults).
- Records remain bind-compatible with existing yml keys (relaxed binding); existing property keys unchanged.
- Reproducible build (pinned external deps); `mvn test` stays green and hermetic.

## Risks / open questions

- Records migration is the binding-risk core; one type per slice, each with its binding test green. Order:
  (1) Embedding+Llm (share the ollama base-url consolidation); (2) Cors; (3) Rag (split + chunk `@AssertTrue` +
  single registration + remove chunker guard); (4) Mcp (nested record + defensive copies + drop
  `working-directory`). Then (5) scripts + remaining doc/yml site cleanup.
- `@AssertTrue` on a record requires a boolean accessor + explicit message (the violation otherwise names the
  derived property, not the keys).
- Removing the `DeterministicTextChunker` guard relies on the boundary `@AssertTrue` always firing first — the
  binding test is the guard.

## Definition of Done (binary)

- [ ] Every assistant-app `@ConfigurationProperties` is a constructor-bound record with `@DefaultValue`
      defaults; no setter-based config bean remains; each has a passing binding test (defaults + populated
      round-trip, incl. nested `McpServer.args`/`env`).
- [ ] `AssistantRagProperties` (or its split records) is `@EnableConfigurationProperties`-d in exactly one place.
- [ ] `grep -c 11434 application.yml` ≤ 1 and `grep -rc 11434 assistant-app/src/main/java` ≤ 1; both embedding
      and chat resolve the single value.
- [ ] A misconfigured `chunk-overlap >= chunk-max-size` fails context startup with a message naming both keys
      (binding test); `DeterministicTextChunker` no longer re-validates it.
- [ ] `working-directory` removed from `AssistantMcpProperties`, both docs/spec sites, and all four yml lines;
      `grep -rn "working-directory\|workingDirectory" assistant-app docs` returns only the new explanatory note.
- [ ] `scripts/mcp-weather` pins an exact ref; `start-assistant.sh` + `application.yml` no longer both hardcode
      the jar version string; `capture-demo-answers.sh` has no bare `python3` JSON dependency.
- [ ] `./mvnw -o test` BUILD SUCCESS and hermetic.

## Round-2 critic resolutions (authoritative; supersede the body where in conflict)

- **[R2-3b-1, blocking] Chunker guard direction locked — keep BOTH layers, do NOT strip the domain guard.**
  `DeterministicTextChunker` is a value object constructed directly (`new DeterministicTextChunker(...)`), so per
  AGENTS §4 it must stay valid-by-construction: keep its three constructor guards (maxChunkSize≥1, overlap≥0,
  overlap<maxChunkSize). The boundary record ADDS `@Positive` (chunk-max-size), `@PositiveOrZero` (chunk-overlap),
  and the cross-field `@AssertTrue(message="assistant.rag.chunk-overlap must be smaller than
  assistant.rag.chunk-max-size") boolean isOverlapSmallerThanMax()` so a misconfiguration fails at binding with a
  clear message. This is intentional defense-in-depth (boundary message + domain invariant), not redundancy to
  delete. The earlier "remove the redundant chunker guard" instruction is withdrawn.
- **[R2-3b-2, major] The Rag slice splits into three sub-slices**, each green before the next: (3a) collapse the
  triple `@EnableConfigurationProperties(AssistantRagProperties)` to one (no record change); (3b) convert to
  record(s) keeping `assistant.rag.*` prefixes + binding test; (3c) chunk `@AssertTrue`/boundary constraints.
- **[R2-3b-3, major] Registration strategy locked: a single owning `@EnableConfigurationProperties`** on one
  config. `@ConfigurationPropertiesScan` is explicitly rejected (it would re-register every type app-wide,
  widening scope beyond the Rag-triplicate fix).
- **[R2-3b-4, major] yml jar-version mechanism locked.** `application.yml` uses `${COUNTRIES_MCP_JAR:...}`
  resolved from an env var that `start-assistant.sh` exports after globbing the newest
  `countries-mcp-server-*.jar`; the version literal then appears in neither file. DoD updated accordingly.
- **[R2-3b-5] Both docs/spec/12 and /13** get the working-directory removal; one shared explanatory note is
  referenced from both (not only 12).
- **[R2-3b-6] Shared-file ordering.** `scripts/capture-demo-answers.sh` is edited by both 3a and 3b; land 3a
  first, then rebase 3b. Recorded as a ship-order dependency, not full independence.
