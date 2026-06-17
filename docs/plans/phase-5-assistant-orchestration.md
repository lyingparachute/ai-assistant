Status: landed — 6223a2d

# Phase 5: Assistant Orchestration

## Scope

- `question` package: `UserQuestion`, `AssistantAnswer`, `AnswerSource`, `ConversationTurn` (request-local audit only).
- `llm` package: `LlmPort`, `PromptContext`, `LlmResult`; Ollama chat outbound adapter via Spring AI (types confined to adapter/configuration).
- `orchestration` package: `SourceRoutingPolicy`, `QuestionRoute`, `AnswerQuestionUseCase`, `ResponseComposer`, `AssistantRequestTrace`.
- Deterministic routing for required demo questions plus CDQ product questions (keyword match on `cdq` / `fraud guard`).
- Wire `CountriesPort`, `WeatherPort`, `RagKnowledgePort`, and `LlmPort` in orchestration; no autonomous tool loop.
- Source-unavailable and insufficient-product-knowledge paths; no model-memory fallback for missing facts.
- Structured request trace logging (correlation id, route, ports invoked, RAG count, outcomes).
- Orchestration tests with controlled port stubs per `docs/spec/07-test-strategy.md` §Assistant Orchestration Tests.
- README Ollama chat model configuration section.

## Out of scope

- Chat Interface / HTTP inbound adapter (Phase 6).
- Demo answer capture, E2E tests, `docs/demo/*` (Phase 7).
- Changes to `countries-mcp-server`, pgvector schema, RAG ingestion pipeline.
- Committing this plan file.

## Definition of Done

- [x] `./mvnw -pl assistant-app test` passes; output recorded in `implementation-notes.md`.
- [x] Controlled-port tests prove routing for all four required demo questions per Phase 5 acceptance criteria in `docs/spec/06-implementation-plan.md`.
- [x] CDQ product question retrieves RAG knowledge and passes grounded snippets to `LlmPort`; no-result path reports insufficient product knowledge.
- [x] `ResponseComposer` distinguishes country facts, weather, RAG knowledge, and general model synthesis in `AssistantAnswer`.
- [x] Combined country-weather path: countries failure blocks weather; weather failure yields partial answer naming weather unavailable.
- [x] Berlin path fires countries + `LlmPort` only; does not fire weather or RAG.
- [x] Ollama unavailable when synthesis required returns source-unavailable; no fabricated facts.
- [x] No Spring AI, MCP SDK, JDBC, or HTTP client types in domain or orchestration code.
- [x] README documents Ollama chat model (`qwen3:4b`) configuration.

## Milestones

- [x] M1 — Domain types (`question`), `LlmPort` + `PromptContext` + `LlmResult`, `AssistantLlmProperties`, `OllamaLlmAdapter`, unit/contract tests
- [x] M2 — `SourceRoutingPolicy`, `QuestionRoute`, routing unit tests
- [x] M3 — `AnswerQuestionUseCase`, port orchestration, `AssistantRequestTrace` logging
- [x] M4 — `ResponseComposer`, source labeling, composer tests
- [x] M5 — Full orchestration integration tests, README, verification

## Review loops

- Per milestone: implement → critic review → clean-code pass (max 3 loops).

## Routing rules (locked)

| Normalized question pattern | Route | Ports invoked |
| --- | --- | --- |
| contains `capital` + `germany` (no weather/temperature) | `COUNTRY_CAPITAL` | Countries |
| contains `temperature` + `munich` (no capital-of-germany chain) | `WEATHER_LOCATION` | Weather |
| contains `temperature` + `capital` + `germany` | `COUNTRY_THEN_WEATHER` | Countries → Weather |
| starts with `what do you know about` + place | `PLACE_SYNTHESIS` | Countries → Llm |
| contains `cdq` or `fraud guard` | `CDQ_PRODUCT` | Rag → Llm |
| otherwise | `UNSUPPORTED` | none (clear message) |
