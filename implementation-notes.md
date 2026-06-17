# Phase 2 Implementation Notes

## Decisions not explicit in the implementation plan

### MCP Java SDK selection

- Library: `io.modelcontextprotocol.sdk:mcp-core` + `mcp-json-jackson2` at BOM `1.0.0`.
- Jackson 2.x bundle chosen to align with Spring Boot 3.5; Jackson 3 `mcp` aggregator avoided.
- Tool registration uses `McpServer.sync(...).toolCall(...)`, not deprecated `.tool()`.

### Configuration naming

- Implemented `CountriesMcpConfiguration` instead of architecture doc name `CountriesMcpProperties`. Same binding prefix `countries.mcp.*`. Renaming deferred to avoid cross-phase churn.

### Package layout

- Added `model/` for `CountryFacts`, `LookupPlace`, `CountryLookupOutcome` — not listed in architecture tree but keeps application layer free of tool types.
- Skipped `services/RestCountriesLookupService`; name-then-capital orchestration lives in `LookupCountryUseCase` per Phase 2 task list (use case + port, not separate service class).

### Hint ownership

- Application hints in `CountryLookupHints`; MCP error category strings in `CountryToolErrors`. Tool layer maps `CountryLookupOutcome` → envelope.

### MCP vs HTTP timeouts

- MCP `requestTimeout` set to `2 × restCountriesTimeoutSeconds` so name-then-capital fallback can complete within one MCP call.

### Stdio safety

- `logback-spring.xml` logs to stderr only.
- `spring.main.banner-mode: off`, `web-application-type: none`.
- `countries.mcp.stdio-enabled` gates `CountriesMcpServerAdapter`; false in test profile.

### Serialization failures

- `JsonProcessingException` maps to `country lookup failed`, not `REST Countries source unavailable`.

### Test stub server

- JDK `HttpServer` stub matches decoded URI paths (not URL-encoded keys) to mirror `HttpServer` path decoding.

## Tradeoffs

- Manual MCP host smoke not captured; automated coverage exercises tool handler and lifecycle instead.
- Weather entry left in `.mcp.json` from Phase 1 placeholder; countries block only updated in Phase 2.

## Follow-up for other plans

- Phase 3: `CountriesPort`, `CountryInfo`, `CountriesMcpClientAdapter` in `assistant-app`.
- Optional: separate `mcpRequestTimeoutSeconds` property if hosts need finer control than `2 ×` HTTP timeout.

---

# Phase 3 Implementation Notes

## Verification

```text
./mvnw -pl assistant-app test
Tests run: 18, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Decisions not explicit in the implementation plan

### MCP client transport

- Uses `io.modelcontextprotocol.sdk` `McpClient.sync` + `StdioClientTransport` + `ServerParameters` (same BOM `1.0.0` as countries server).
- `StdioMcpToolInvoker` is `@Profile("!test")`; tests inject `StubMcpToolInvoker`.
- One `McpSyncClient` per configured tool name, initialized at bean creation for non-test profiles.

### Configuration

- `AssistantMcpProperties` under prefix `assistant.mcp.*` with nested `countries` and `weather` server blocks.
- `working-directory` is documented and bound but not passed to `ServerParameters` (Java MCP SDK 1.0.0 has no cwd builder). Launch from repository root or set command accordingly.

### Port outcomes

- `ToolExecutionResult` sealed hierarchy: `Success`, `ToolError` (tool `ok: false` envelope), `SourceUnavailable` (transport, empty payload, malformed mapping, weather provider error text).
- `WeatherTimestamp.Retrieved` used for `semdin/mcp-weather` because the server exposes no observed time.

### Package layout

- `McpOutboundConfiguration` lives in `adapters.outbound.mcp` so package-private mappers stay package-local.
- Response mappers are package-private; adapters are public.

### Weather text parsing

- Regex maps `the weather in <city> is currently: <celsius>` per captured upstream contract.
- `Location` uses the requested city name, not the parsed segment, so display stays aligned with the user question.

## Tradeoffs

- Eager MCP client initialization on app start may spawn subprocesses early; lazy init deferred until orchestration needs it.
- Live weather manual smoke not captured; automated stubs cover mapping and failure paths.

## Follow-up for other plans

- Phase 5: wire `CountriesPort` and `WeatherPort` into deterministic source routing.
- Optional: lazy MCP client pool or shared transport if startup cost becomes an issue.

---

# Phase 4 Implementation Notes

## Verification

```text
./mvnw -pl assistant-app test
Tests run: 83, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Total time:  26.539 s
Finished at: 2026-06-15T22:31:51+02:00
```

## Decisions not explicit in the implementation plan

### Product knowledge port

- `ProductKnowledgePort` separates CDQ fetch/extract from `RagKnowledgePort` storage/retrieval. `CdqProductKnowledgeAdapter` maps fetch/extraction failures to `ProductPageResult.SourceUnavailable` without inventing page text.

### Test embedding adapter

- `DeterministicTestEmbeddingAdapter` implements `EmbeddingPort` in tests with `search_document:` / `search_query:` prefix signals and token-hash vectors (dimension 768). Testcontainers RAG tests avoid live Ollama and live CDQ HTTP.

### Fixture-based ingestion tests

- `FixtureProductKnowledgePort` + `RagIngestionTestConfiguration` feed `fixtures/rag/cdq-fraud-guard-sample.html` through the real `ProductPageTextExtractor` while stubbing network fetch.

### Ingestion entry point

- `RagIngestionCommand` is an `ApplicationRunner` gated by `--ingest-rag` or `ASSISTANT_INGEST_RAG=true`; exits with code `1` on `RagIngestionResult.SourceUnavailable`.

### Schema ownership

- Custom `rag_chunks` table with `vector(768)` and JDBC repository; Spring AI supplies only the Ollama embedding client in the outbound adapter (ADR `0001`, `0007`).

## Tradeoffs

- Deterministic test embeddings reward token overlap; fixture retrieval integration uses the configured `0.5` relevance threshold and a product question aligned with fixture phrases. Adapter-level pgvector tests still use `0.3` where the goal is repository similarity mechanics, not production threshold policy.
- Live CDQ page fetch and live Ollama embedding are manual/runtime paths only; automated tests use fixtures and deterministic embeddings.
- `spring.datasource` auto-configuration stays excluded; RAG uses a dedicated Hikari pool bound from `assistant.rag.jdbc-url`.

## Follow-up for other plans

- Phase 5 M1 (landed): `question` domain types, `LlmPort` + `OllamaLlmAdapter`, `AssistantLlmProperties` (`assistant.llm.*`, default `qwen3:4b`). `ChatModel` built inside `LlmPort` bean only (not exposed). `UserQuestion` always trims on construction. See M2+ for routing and orchestration.

---

# Phase 5 Implementation Notes (in progress)

## M1 decisions

- `AnswerSource` uses sealed variants with static factories (`used`, `unavailable`, `insufficient` for RAG only).
- `ModelSynthesis.used()` carries no text — synthesis text lives in `AssistantAnswer.answerText`; source marks contribution only.
- `OllamaLlmAdapter` prompt: instructions → optional grounded facts bullets → user question.
- Test profile excludes Ollama chat beans; orchestration tests will use `StubLlmPort`.

## M1 verification

```text
./mvnw -pl assistant-app test
Tests run: 120, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## M2 decisions

- `RoutedQuestion` carries route-specific data via `Optional` fields validated in compact constructor.
- Place name extraction strips trailing sentence punctuation (`?`, `.`, `!`) for clean `CountriesPort` lookup.

## M3–M4 decisions

- `AnswerQuestionUseCase` returns `ConversationTurn` (request-local audit per ADR 0006).
- `ResponseComposer` keeps synthesis text in `AssistantAnswer.answerText`; `ModelSynthesis` source marks contribution only.
- `AssistantRequestTrace` logs via SLF4J with correlation UUID; tests use controlled ports, not log assertions.
- `OrchestrationConfiguration` wires `AnswerQuestionUseCase` only when all four ports are present (`@ConditionalOnBean`).
- Source-unavailable user messages use labels: Countries MCP, Weather MCP, RAG knowledge, Ollama chat.

## M3–M5 verification

```text
./mvnw -pl assistant-app test
Tests run: 153, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Finished at: 2026-06-16T13:10:xx+02:00
```

## Follow-up for other plans

- Phase 6: API-only `assistant-app` (`POST /api/chat` + CORS) + separate **`chat-ui/` Astro frontend** — **no Thymeleaf** (see `docs/plans/phase-6-chat-interface.md`).
- Phase 7: demo answer capture including CDQ product showcase question.

## Phase 6 UI decision (pre-implementation)

- Thymeleaf / server-rendered Spring pages rejected.
- Chat Interface = new `chat-ui/` Astro app at repo root; backend stays JSON REST.
- Local dev: two processes (Spring Boot + `npm run dev` in `chat-ui/`).

---

# Phase 6 Implementation Notes

## M1 decisions

- `docs/spec/14-assistant-api-contract.md` locks `POST /api/chat` JSON shapes before code.
- `ChatController` is registered through `ChatWebConfiguration` (`@Import` on `AssistantApplication`) because `@ConditionalOnBean(AnswerQuestionUseCase.class)` on a `@RestController` did not reliably match bean registration order during component scan.
- `HttpInboundConfiguration` registers CORS for `/api/**` whenever the HTTP adapter module is active.
- `ChatHttpMapper` is package-private and registered as a `@Component`, constructor-injected into `ChatController` (pure translation, no orchestration). It is also directly instantiable for contract tests.
- Source entries use Jackson `@JsonTypeInfo` discriminator `type` matching contract (`countries_facts`, `weather_observation`, `rag_knowledge`, `model_synthesis`).
- `assistant.cors.allowed-origins` defaults to `http://localhost:4321`; CORS applies to `/api/**`.
- Source-unavailable orchestration outcomes return HTTP `200` with structured body per contract.

## M1 verification

```text
./mvnw -pl assistant-app test
Tests run: 153, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## M2 decisions

- Minimal Astro template at `chat-ui/` (single `index.astro`, no router, no islands framework).
- `PUBLIC_ASSISTANT_API_URL` defaults to `http://localhost:8080`; browser `fetch` targets `/api/chat`.
- `sourceDisplay.ts` renders API `sources[]` verbatim — no second fact-formatting layer.
- Weather timestamp label uses API `timestamp.kind` (`observed` → Observed, `retrieved` → Retrieved).
- No localStorage, no multi-turn scrollback (ADR `0006`).

## M2 verification

```text
cd chat-ui && npm run build
BUILD Complete (1 page)
```

## M3 decisions

- `ChatContractTest` pins Jackson JSON shapes for `ChatRequest`/`ChatResponse` and discriminated `sources[]`.
- `ChatControllerIntegrationTest` uses standalone `MockMvc` with controlled `AnswerQuestionUseCase` mock (happy `200`, blank `400`, source-unavailable `200`).
- `ChatController` is `@ConditionalOnBean(AnswerQuestionUseCase.class)` so test-profile `@SpringBootTest` contexts without orchestration still start.
- README documents two-process startup (backend + `chat-ui`), ports, and `PUBLIC_ASSISTANT_API_URL`.

## M3 verification

```text
./mvnw -pl assistant-app test
Tests run: 160, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Manual smoke (requires live backend + dependencies): start `assistant-app`, start `chat-ui` dev server, submit one question, confirm `answerText` and `sources[]` render in the browser.

---

# Phase 7 Implementation Notes

## M1 — e2e-tests

- `RequiredDemoQuestionsE2ETest` calls live `POST /api/chat` via `java.net.http.HttpClient`.
- Tests assert routing/source fields and trace ids; weather temperature values are not hardcoded.
- Tests skip when the assistant is not reachable (`assumeTrue`).

## M2 — demo capture

- `./scripts/capture-demo-answers.sh` captures JSON to `docs/demo/capture/`.
- Evidence written to `docs/demo/final-answers.md`, `docs/demo/demo-run-log.md`, `docs/demo/request-traces/`.
- Capture hit upstream blockers: REST Countries v3.1 deprecated, `WEATHER_API_KEY` unset, RAG not ingested.

## M3 — runtime wiring fixes for local stack

- `assistant.mcp.countries` uses pre-built jar (`java -jar`) instead of nested `mvnw spring-boot:run`.
- MCP paths relative to `assistant-app` module cwd (`../...`).
- `StdioMcpToolInvoker` sets `initializationTimeout` from configured timeout.
- `ChatController` is registered via `ChatWebConfiguration` + `@Import` on `AssistantApplication` to fix `@ConditionalOnBean` ordering for `ChatController`.
- `RagIngestionMode` activates profile `ingest-rag` with `WebApplicationType.NONE`, skipping MCP subprocess startup so ingestion can run while another assistant instance holds port 8080.
- `StdioMcpToolInvoker` forwards host `WEATHER_API_KEY` / `WEATHER_API_URL` to the weather MCP subprocess when not set in `application.yml`.
- `scripts/mcp-weather` bootstraps semdin/mcp-weather into `.local/`.

## M3 verification

```text
./mvnw -pl e2e-tests test
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Chat UI smoke: `http://localhost:4321` returned HTTP 200 during capture session.

## Follow-up for other plans

- Migrate REST Countries client to v5 API (requires API key) to unblock country-fact demos.
- Configure `WEATHER_API_KEY` for live weather demos.
- Re-run RAG ingestion after stopping the chat assistant (`ASSISTANT_INGEST_RAG=true` uses non-web `ingest-rag` profile).

---

# Code-Quality Audit Refactors (branch `review/code-quality-audit`)

Running log for the `docs/plans/refactor-*.md` ExecPlans, executed via subagent-driven
development (implementer → spec-compliance reviewer → clean-code/quality reviewer, looped
max 3 until clean). Each milestone committed separately; plan files not committed.

Execution order: 3a → 5a → 1 → 2 → 3b → 4 → 5b.

## refactor-3a — e2e demo honesty gating

- `RequiredDemoQuestionsE2ETest` (surefire `*Test`, green-skipped via `assumeTrue`) renamed to
  `RequiredDemoQuestionsIT` (failsafe). Both `assumeTrue(client != null)` guards removed; the
  `@BeforeAll` connect step now throws when the assistant is unreachable, so the opt-in command
  fails (non-zero) instead of green-skipping.
- `maven-failsafe-plugin` declared in `e2e-tests/pom.xml` under a new `e2e` profile (inheriting the
  parent's managed `integration-test`+`verify` execution). The module stays compiled in the default
  reactor; `mvn test` and `mvn verify` (no profile) run no IT; only `./mvnw verify -P e2e` runs the
  demo verification.
- Single authoritative demo-question set: `e2e-tests/src/test/resources/demo-questions.json`
  (superset of 6, each keyed with a source-path key). `DemoQuestions` loader reads it; the IT
  subsets it by key (same assertions as before, no coverage expansion); `capture-demo-answers.sh`
  reads all 6. CDQ wording reconciled to one canonical string ("What is CDQ Fraud Guard?").
- README §Tests stale `./mvnw -pl e2e-tests test` replaced with `./mvnw verify -P e2e`; README and
  `docs/spec/08-demo-plan.md` now reference the shared question file instead of inlining lists.
- Verification (offline, no server / dead port): `./mvnw -o test` BUILD SUCCESS, no demo report;
  `./mvnw -o verify` (no profile) runs no failsafe; `./mvnw -o verify -P e2e` against a dead base-url
  → BUILD FAILURE (fail-not-skip); `grep assumeTrue e2e-tests/src` empty.
- Code-review follow-up (commit after 58f48bc): capture script reads the question file into a
  variable (so a malformed/missing/empty file fails non-zero, no silent process-substitution skip)
  and exits with a clear message on empty input; `DEMO_QUESTIONS_FILE` env override added for
  testability. `sourcePathKey` is now consumed: each captured file records `expectedSourcePathKey`
  next to the response, so demo evidence shows the source path per question (AGENTS §12). Failure
  modes verified: malformed JSON / missing file / empty `questions` each exit 1.

## refactor-5a — component-scan filter, controller wiring & context smoke test

- `AssistantApplication`'s direct `@ComponentScan(excludeFilters = {ChatController, ChatWebConfiguration})`
  is deleted, restoring Spring Boot's default `TypeExcludeFilter` + `AutoConfigurationExcludeFilter`
  (a direct `@ComponentScan` had suppressed the meta-annotated one, letting `@TestConfiguration`
  classes under the base package auto-scan in `@SpringBootTest`). `@Import` now carries only
  `OrchestrationConfiguration.class`.
- `ChatWebConfiguration` is **deleted**. `ChatController` is now a plain component-scanned
  `@RestController` with constructor injection — no `@Bean` factory, no `@ConditionalOnBean` on the
  endpoint. A missing `AnswerQuestionUseCase` is now a startup failure, not a silent 404. The
  `@ConditionalOnBean(...)` guard survives only on `answerQuestionUseCase` in
  `OrchestrationConfiguration` (a component-scanned config processed after the outbound-adapter
  configs, so its ports are registered in time).
- **Supersession (not a history rewrite):** the present-tense `ChatWebConfiguration` wiring
  described at `implementation-notes.md:207` (Phase 6) and `:275` (Phase 7), and the runtime-fix
  note in `docs/demo/demo-run-log.md` ("ChatController registered via `@Import` + `@Bean`
  (ChatWebConfiguration)"), all describe the pre-refactor state. Those lines are dated
  chronological / captured-evidence records and are left intact (rewriting them would erase the
  record / fabricate evidence per AGENTS §12); this refactor-5a note is the current authoritative
  description and **supersedes** them.
- New hermetic `AssistantContextLoadTest` (`@SpringBootTest`, profile `test`, imports no existing
  `@TestConfiguration`): registers stub external ports via an `ApplicationContextInitializer`
  (`support/ChatPathPortStubs` + a local `McpToolInvoker` stub) before refresh, because
  `@ConditionalOnBean` is evaluated against definitions present at config-parse time — `@Import`ed
  or `@MockitoBean` stubs register too late. Asserts the context boots, `ChatController` +
  `AnswerQuestionUseCase` are present, and `getBeanNamesForType(Clock.class)` is exactly
  `["systemClock"]` (guards the X-C-2 single-Clock wiring). Verified falsifiable: re-adding the scan
  filter makes auto-scanned `McpTestConfiguration` add a second `@Primary testClock`, failing the
  Clock assertion.
- Regression from making `ChatController` unconditional: four full-context outbound-adapter
  `@SpringBootTest` classes (`CountriesMcpClientAdapterIntegrationTest`,
  `WeatherMcpClientAdapterIntegrationTest`, `PgvectorRagAdapterIntegrationTest`,
  `RagRetrievalIntegrationTest`, `RagIngestionUseCaseIntegrationTest`) boot the full app but do not
  wire `LlmPort` (MCP-only tests also lack `RagKnowledgePort`), so the now-unconditional controller
  failed at context load. `support/ChatPathPortStubs` registers a stub `LlmPort` and a non-`@Primary`
  stub `RagKnowledgePort` before refresh so the `@ConditionalOnBean` guard is satisfied without
  re-masking the scan leak; contexts with their own `@Primary RagKnowledgePort` keep it. Stubs are
  wiring-only and throw `UnsupportedOperationException` if invoked.
- Follow-up for **refactor-1** (out of scope here): the stale claim at `implementation-notes.md:208`
  ("`ChatHttpMapper` is package-private, instantiated inside `ChatController`") no longer matches the
  code — `ChatHttpMapper` is a `@Component` injected via the constructor. refactor-1 owns the
  `ChatHttpMapper` rewrite and should correct that line.


---

# refactor-1 Implementation Notes (assistant-app domain typed model)

- `SourceUnavailability` value object lives in `tools` (sourceLabel/message/hint, all non-blank). It
  is carried by the `SourceUnavailable` variants of `ToolExecutionResult` and `LlmResult`; the three
  loose fields were replaced by one VO component. A package-private 3-arg convenience constructor and
  delegating `sourceLabel()/message()/hint()` accessors are kept so adapter construction sites and
  their contract tests stay unchanged. `rag` result types and `llm.EmbeddingResult` are untouched
  (owned by refactor-2).
- `RoutedQuestion` is now a sealed interface with one record per route; `QuestionRoute` is retained
  (each variant exposes `route()`) so trace `route=`/`outcome=` tokens are unchanged. The four
  parallel `Optional` fields, `validateRouteFields`, and all `orElseThrow` calls are gone.
- `AnswerSource.{CountriesFacts,WeatherObservation,RagKnowledge,ModelSynthesis}` are sealed
  interfaces with `Used`/`Unavailable`(/`Insufficient`) record variants — boolean flags, nullable
  payloads, and cross-field guards removed; invalid states are unrepresentable by construction. The
  `used(...)/.unavailable(...)/.insufficient()` static factories are preserved on the sealed parents
  so construction sites and `ChatContractTest` are unchanged. The `AnswerSource.*.Unavailable`
  variants carry `unavailableMessage`/`unavailableHint` directly (no source label): the HTTP JSON
  contract never serializes a label, and the failing-source label in answer text comes from
  `ResponseComposer` constants. (Plan O-4 — render label from the VO — was dropped in round-2 because
  the VO label casing differs from the composer constants and would change user-visible text.)
- `AssistantAnswer` models the trace correlation id as an empty-string-sentinel typed absence
  (`traceCorrelationId()` returns `Optional`); the `hasTraceCorrelationId` boolean and its guards are
  gone.
- `ResponseComposer` failure methods take `SourceUnavailability`; the two byte-identical
  countries-unavailable methods collapsed into one (`composeCountriesUnavailable`).
- Synthesis sub-flow (place + CDQ) extracted to one private `synthesize(...)` helper taking the
  prompt and two outcome composers (O-6). `CAPITAL_FACT_TEMPLATE` has one owner
  (`ResponseComposer`), referenced by the use case (O-5).
- `AssistantRequestTrace` owns outcome-string formatting (`completed(int answeredSourceCount)`); the
  `"pending"` and `%s_answered_sources=%d` literals are named constants. The immutability restructure
  (O-10) was consciously declined per the plan.
- `SourceRoutingPolicy` carries a comment that routing is demo-scoped; O-12 characterization test
  (`offDemoCapitalQuestionFallsThroughToUnsupported`) pins that "capital of France" → UNSUPPORTED.
- The stale `implementation-notes.md` claim about `ChatHttpMapper` being instantiated inside
  `ChatController` was corrected (it is a constructor-injected `@Component`).
- Verification: `./mvnw -o test` BUILD SUCCESS; assistant-app 178 tests (was 169), countries 18, all
  green. `ChatContractTest` JSON-shape assertions unchanged (diff is additive: one new
  characterization test for the unavailable-source HTTP shape).

## refactor-2 — RAG ingestion & port redesign (landed)

- `RagKnowledgePort` is exactly three methods, all returning sealed typed results, none throwing:
  `retrieve` → `RagRetrievalResult`, `storeChunks` → `ChunkStorageOutcome`, `findContentHashForSource`
  → `StoredSourceState`. `countChunksForSource` is gone.
- `StoredSourceState` = `Stored(contentHash, chunkCount)` | `Absent` | `Unavailable(SourceUnavailability)`
  carries the chunk count UNCHANGED needs, so dropping `countChunksForSource` left no orphan read
  (round-2 [P2R2-A/B]). DB-down during the read is a typed `Unavailable`, never an uncaught throw and
  never misread as a first ingestion (fixes R-5).
- Outcome classification lives entirely in `RagIngestionUseCase`; the outbound adapter constructs no
  `RagIngestionReport`. `storeChunks` returns `ChunkStorageOutcome.Stored(INGESTED|REPLACED)`; the
  use case builds the report. INGESTED vs REPLACED is derived from the pre-delete row count returned
  by `PgvectorIngestionRepository.replaceChunksForSource`, which now uses one
  `transactionTemplate.execute` (delete-count + inserts in a single transaction) — kills the TOCTOU.
- `SourceUnavailability` VO (owned by `tools`, from refactor-1) is adopted by `RagIngestionResult`,
  `RagRetrievalResult`, `EmbeddingResult` (llm), and `ProductPageResult`, each via a convenience
  constructor + delegating accessors + `asUnavailability()`. `AnswerQuestionUseCase`'s RAG-unavailable
  branch now uses `.asUnavailability()` (refactor-1 follow-up). Edges `rag→tools`, `llm→tools`; `tools`
  imports neither (leaf) → no cycle.
- `fromStoredChunk` had no production caller (grep-confirmed) → stored shape deleted. `KnowledgeSnippet`
  carries a mandatory `RetrievalScore` value object (no boolean flag, no sentinel double, no Optional).
  `AnswerSourceTest`, `ResponseComposerTest`, `ChatHttpMapper` switched to the retrieval factory/score.
- `EmbeddingDimensions.matches(float[])` predicate replaces the hand-rolled length comparison in
  `OllamaEmbeddingAdapter`. pgvector cosine-distance→similarity expression extracted once with an
  explaining comment; `"similarity"` alias and `%.8f` literal are named constants.
- Schema init only via `PgvectorSchemaInitializer` (now public for test injection); the adapter
  pass-through is gone. Production and test share `PgvectorBeansConfiguration` (a profile-neutral
  `@Import`-only class, not `@Configuration`, so component scan ignores it). The test config imports
  it and adds only a `@Primary` test `EmbeddingPort` (registered first via a dedicated config so the
  `@ConditionalOnBean(EmbeddingPort)` guard resolves). pgvector integration tests inject the
  initializer in `@BeforeEach`.
- `RagIngestionMode` collapsed to one package-private `enabled(ApplicationArguments, Function<String,String> env)`;
  public overloads delegate with `System::getenv`; `main` builds `ApplicationArguments` once. Env path
  is unit-tested via a fake env function.
- New tests: real failing-DB Testcontainers test (`RagIngestionSourceUnavailableIntegrationTest`
  stops the pgvector container, asserts `RagIngestionResult.SourceUnavailable` label `pgvector RAG`,
  no throw); chunker overlap + exact-multiple boundary; retrieval `SourceUnavailable` for embedding
  failure and query-throw; env-enabled `RagIngestionMode`; embedding prefix tests assert the returned
  vector (not `verify(...)`).
- Verification: `./mvnw -o test` BUILD SUCCESS; assistant-app 189 tests, countries-mcp 18, all green.

## refactor-3b — config records & build hygiene

- All five assistant-app `@ConfigurationProperties` are now validated constructor-bound records with
  `@DefaultValue` defaults, mirroring countries-mcp-server. Defaults moved off Java field initializers
  (constructor binding ignores those) onto `@DefaultValue` components.
- Ollama base URL lives once: yml key `assistant.ollama.base-url`; `assistant.embedding.ollama-base-url`
  and `assistant.llm.ollama-base-url` resolve it via the Spring placeholder `${assistant.ollama.base-url}`.
  The literal appears once in yml, zero times in Java (the embedding/llm records carry no default for it).
- `AssistantRagProperties` split into three records at the SAME `assistant.rag.*` prefix (no key rename):
  `AssistantRagStorageProperties` (jdbc/user/pass), `AssistantRagRetrievalProperties`
  (top-k/relevance-threshold/source-url/fetch-timeout-seconds), `AssistantRagChunkingProperties`
  (chunk-max-size/chunk-overlap). Multiple records binding one prefix is allowed; each takes its subset.
  `@EnableConfigurationProperties` for all three lives in exactly one place (`RagApplicationConfiguration`);
  the duplicate registrations on `CdqOutboundConfiguration`/`PgvectorOutboundConfiguration` were removed.
- Chunk validation is defense-in-depth (R2-3b-1): the chunking record adds `@Positive`/`@PositiveOrZero`
  plus a cross-field `@AssertTrue boolean isOverlapSmallerThanMax()` with a key-named message; a
  `ApplicationContextRunner` test proves a bad config fails context startup with that exact message.
  `DeterministicTextChunker` keeps its three constructor guards (it is also constructed directly).
- `AssistantMcpProperties` is a record with a nested `McpServer` record; `args`/`env` defensive copies
  (`List.copyOf`/`Map.copyOf`) live in the `McpServer` compact constructor. The dead `working-directory`
  property is gone (MCP SDK 1.0.0 has no cwd setter) — removed from the record, both docs/spec/12 and 13,
  and all four yml lines. A shared note in docs/spec/12 (`#mcp-subprocess-cwd`, referenced from 13)
  documents the real cwd assumption: `spring-boot:run` launches from `assistant-app/`, so the jar path
  is relative to that directory.
- Binding-test harness `PropertiesBinding` uses `Binder.bindOrCreate` + `ValidationBindHandler` so it
  mirrors Spring Boot's startup binding (defaults applied for an absent prefix, validation enforced).
- Scripts: `scripts/mcp-weather` pins upstream `semdin/mcp-weather` to commit
  `8bb7bd1b8fa7364e6f0ea7772be48c25f4a38038` with an untrusted-input comment. `start-assistant.sh`
  globs the newest `countries-mcp-server-*.jar` (excluding `-sources`/`-javadoc`) and exports
  `COUNTRIES_MCP_JAR`; `application.yml` reads `${COUNTRIES_MCP_JAR:...}`, so the version string is
  pinned in neither the script nor (twice in) yml — it survives only as the yml fallback default.
  `capture-demo-answers.sh` keeps python3 for correct JSON quoting but adds an explicit
  `require_command python3` (and `curl`) guard that fails loudly if the interpreter is missing
  (S-2: explicit guard chosen over a jq rewrite to avoid quoting-bug risk in payload construction).
