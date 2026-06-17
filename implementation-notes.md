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

- Phase 5: wire `RagKnowledgePort` into deterministic source routing and `LlmPort` synthesis; return explicit insufficient-product-knowledge when retrieval is `NoRelevantKnowledge`.
- Phase 5: `ResponseComposer` and chat inbound adapter.
- Phase 7: capture demo answers for CDQ product questions from a running assistant after orchestration lands.

