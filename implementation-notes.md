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
- `ChatController` and `HttpInboundConfiguration` are `@ConditionalOnBean(AnswerQuestionUseCase.class)` so existing test-profile `@SpringBootTest` contexts without full orchestration wiring still start.
- `ChatHttpMapper` is package-private, instantiated inside `ChatController` (no Spring bean — mapper is pure translation).
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

## Follow-up for other plans

- Phase 7: demo answer capture including CDQ product showcase question and `e2e-tests/` expansion.

