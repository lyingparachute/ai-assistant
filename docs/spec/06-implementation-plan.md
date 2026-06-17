# Implementation Plan

This plan is documentation-first. Production source code must not be implemented until architecture intent, acceptance criteria, test strategy, demo plan, risks, and ADRs are in place.

## Phase 0: Documentation and Repository Rules

### Goal

Lock the project language, architecture direction, acceptance criteria, test strategy, demo evidence rules, risks, and architecture decisions before production implementation starts.

### Tasks

- Keep `CONTEXT.md`, `README.md`, `AGENTS.md`, and `docs/spec/` aligned.
- Add architecture, implementation, test, demo, and risk documents.
- Add ADRs for framework, model, vector database, MCP, architecture style, and memory scope.
- Confirm that final demo answers are not documented until captured from the running assistant.

### Expected Files and Modules

- `CONTEXT.md`
- `README.md`
- `AGENTS.md`
- `docs/spec/`
- `docs/adr/`

### Acceptance Criteria

- `CONTEXT.md` and `AGENTS.md` exist and are consistent with the specs and ADRs.
- `docs/spec/01-product-specification.md`, `docs/spec/02-non-functional-requirements.md`, `docs/spec/03-acceptance-criteria.md`, and `docs/spec/04-glossary.md` exist.
- `docs/spec/05-architecture.md` exists and explains the selected module boundaries and request flows.
- `docs/spec/06-implementation-plan.md` exists and defines phases 0 through 8.
- `docs/spec/07-test-strategy.md`, `docs/spec/08-demo-plan.md`, and `docs/spec/09-risk-register.md` exist.
- `docs/spec/10-code-quality-guidelines.md` exists and is the single source of detail for code conventions.
- ADRs `0001` through `0007` exist under `docs/adr/`.
- The local skills under `.agents/skills/**` exist and reference the specs and ADRs instead of duplicating them.
- No production Java source code is introduced in this phase, and no modules are scaffolded.

### Suggested Commit Message

`docs: define assistant architecture plan`

## Phase 1: Build Skeleton

### Goal

Create the repository structure and build configuration for a multi-module local Java assistant without implementing assistant behavior yet.

### Tasks

- Create modules `assistant-app`, `countries-mcp-server`, and `e2e-tests`. Do not create `shared-kernel` yet: add it only when a concrete type must be shared between `assistant-app` and `countries-mcp-server`.
- Select and configure Java version, build tool, Spring Boot, and Spring AI dependency management.
- Add placeholder application entry points only where required for build health.
- Add shared test conventions and code style settings.
- Add local MCP configuration convention, such as `.mcp.json`, without hardcoding machine-specific paths.
- Update `README.md` with build and test commands once verified.

### Expected Files and Modules

- Root build files.
- `assistant-app/`
- `countries-mcp-server/`
- `e2e-tests/`
- `shared-kernel/` only if a concrete cross-module type already exists.
- README setup sections.

### Acceptance Criteria

- A clean checkout can run the documented build command.
- All modules compile with no assistant production behavior implemented beyond bootstrapping.
- Dependency versions and local runtime expectations are documented.
- No secrets, local paths, or machine-specific configuration are committed.

### Suggested Commit Message

`build: add local assistant module skeleton`

## Phase Dependencies and Exit Gates

The phases are ordered because later behavior depends on earlier verified boundaries. A phase is not complete until its focused test command has been run and the actual output has been recorded in phase notes, `docs/ai/`, or the later demo run log.

- Phase 2 must not start until the Phase 1 build and test commands pass from a clean checkout.
- Phase 3 requires the Phase 2 countries MCP server to be runnable from documented local MCP configuration.
- Phase 4 requires the Phase 1 skeleton and the ADR `0007` embedding decision. Live ingestion requires Ollama `nomic-embed-text`; automated tests may use controlled embedding adapters where external model runtime would make tests unstable.
- Phase 5 requires Phase 3 MCP adapters, Phase 4 RAG retrieval, and controlled or local `LlmPort` behavior.
- Phase 6 requires Phase 5 orchestration callable from application code.
- Phase 7 requires Phases 2 through 6 exit gates to pass. Phase 7 captures live demo evidence; it does not replace focused tests that belong with each implemented behavior.
- Phase 8 requires Phase 7 evidence files and clean-checkout command verification, or explicit documented blockers.

Risk gates:

- Ollama missing: Phase 4 and Phase 5 document health checks and source-unavailable behavior for embedding and synthesis paths.
- Weather configuration missing: Phase 3 tests source-unavailable behavior and Phase 7 records any missing live weather configuration honestly.
- MCP startup failure: Phase 2 and Phase 3 test startup/configuration failures and document local startup commands.
- CDQ extraction failure: Phase 4 surfaces source-unavailable ingestion failure and never fabricates product knowledge.
- Weak demo evidence: Phase 5 emits traceable route/source data and Phase 7 stores one trace excerpt per required question.

## Phase 2: Countries MCP Server

### Goal

Build the custom MCP server that exposes country facts through REST Countries.

### Prerequisites

- Phase 1 build and test commands pass from a clean checkout.
- MCP transport and Java MCP library are selected for stdio JSON-RPC before handler implementation.

### Tasks

- Document the countries MCP tool contract before coding: semantic tool name, input schema, compact output fields, and structured error envelope with recovery hint. See `docs/spec/11-countries-mcp-tool-contract.md`.
- The tool accepts a country name or a capital-city name, so "Germany" and "Berlin" both resolve to Germany.
- Design MCP tool names, descriptions, and JSON schemas as semantic assistant-facing tools, not a REST Countries API mirror.
- Structure the MCP server around a small hexagonal slice: inbound MCP adapter, lookup use case, `RestCountriesPort`, outbound REST Countries adapter, schemas, configuration, and typed error helpers.
- Document REST Countries API version, base URL, endpoints, field mapping, and capital-city resolution behavior, including not-found and ambiguous-capital handling.
- Implement REST Countries outbound adapter with configured base URL and timeout.
- Translate REST Countries responses into a server-owned value object and MCP JSON output. Do not introduce `shared-kernel` or assistant-side `CountryInfo` in this phase.
- Surface invalid-country and source-unavailable outcomes with recovery hints.
- Add graceful startup and shutdown behavior for local stdio execution.
- Add contract tests for tool name, schema, output fields, error envelope, and absence of raw upstream JSON.
- Add integration-style tests with a stubbed REST Countries HTTP server or controlled fixtures. Automated tests must not call the live REST Countries service.
- Document how to run the countries MCP server locally.
- Update `.mcp.json` and README with verified command, args, env, working directory, stdio transport, and timeout.

### Expected Files and Modules

- `countries-mcp-server/`
- `shared-kernel/` only if a concrete cross-module Java type is named and justified. MCP JSON is the default boundary, so no shared Java type is expected in this phase.
- Test fixtures for REST Countries responses.
- README countries MCP setup section.

### Acceptance Criteria

- `./mvnw -pl countries-mcp-server test` passes and the actual output is recorded in the phase notes or later demo run log.
- A Germany lookup through the MCP tool returns Berlin with country name, capital, region, and population fields from controlled REST Countries responses.
- A Berlin capital-city lookup through the same MCP tool resolves to Germany with the same required fields.
- Unrecognized country or capital input returns a structured tool error with a recovery hint, and the MCP server process stays alive.
- REST Countries timeout or HTTP error returns a source-unavailable structured tool error, and the MCP server process stays alive.
- Contract tests pin tool name, required input and output fields, error envelope shape, and confirm tool output does not expose raw upstream JSON.
- Startup rejects invalid configuration before tool registration; graceful shutdown leaves no hanging local MCP process.
- `.mcp.json` launches the countries server over documented stdio transport; README documents a manual smoke run from that configuration.
- Tests do not require uncontrolled network access.

### Suggested Commit Message

`feat: add countries mcp server`

## Phase 3: MCP Client Integrations

### Goal

Integrate the assistant application with the custom countries MCP server and local `semdin/mcp-weather` MCP server through adapter-owned boundaries.

### Prerequisites

- Phase 2 countries MCP server is runnable from documented local MCP configuration.
- Weather MCP tool contract is discovered and captured in controlled fixtures before adapter mapping.

### Tasks

- Define `CountriesPort`, assistant-side `CountryInfo`, and `CountriesMcpClientAdapter` in `assistant-app`.
- Add countries adapter tests for Germany and Berlin lookups using controlled MCP fixtures or a test server.
- Define `WeatherPort`, `WeatherReport`, and `WeatherTimestamp` in the assistant boundary. `WeatherTimestamp` distinguishes observed time from retrieval time; a retrieval time is never relabeled as observed.
- Implement `WeatherMcpClientAdapter` behind `WeatherPort`.
- Map MCP tool results and failures to typed port outcomes, not raw MCP payloads.
- Validate malformed tool payloads at the adapter boundary and surface clear boundary failures.
- Bind countries and weather MCP command, args, env, transport, and timeout through typed configuration. Do not commit secrets.
- Capture the `semdin/mcp-weather` tool contract in controlled fixtures and add contract tests for tool name, required fields, success mapping, and structured error mapping.
- Add integration-style adapter tests with controlled MCP responses; cover Munich success, missing server/configuration failure, and malformed weather payload handling.
- Document weather MCP installation, required environment variables, local startup, and optional tagged manual verification against a live server.

### Expected Files and Modules

- `assistant-app/` countries and weather ports and adapters.
- `assistant-app/` MCP adapter tests and fixtures.
- README countries and weather MCP setup sections.

### Acceptance Criteria

- `./mvnw -pl assistant-app test` passes for MCP adapter tests and the actual output is recorded.
- Germany and Berlin lookups succeed through `CountriesPort` using controlled MCP fixtures or a test server.
- A controlled Munich weather MCP response maps to `WeatherReport` with location, temperature, and `WeatherTimestamp` provenance.
- Countries and weather MCP failures return source-unavailable outcomes; no adapter invents country facts or temperature values.
- Contract tests pin countries and weather MCP schema mappings and error shapes.
- End-to-end demo answers remain deferred to Phase 7; this phase verifies port and adapter boundaries only.

### Suggested Commit Message

`feat: integrate mcp tool adapters`

## Phase 4: pgvector and RAG Ingestion

### Goal

Create repeatable ingestion and retrieval for CDQ Fraud Guard product-page RAG knowledge.

### Prerequisites

- Phase 1 build and test commands pass.
- Ollama embedding model `nomic-embed-text` is available locally or replaced by a controlled embedding adapter in tests.
- pgvector schema dimension is fixed at `vector(768)` per ADR `0007`.

### Tasks

- Define `RagKnowledgePort`, `KnowledgeSnippet`, `RagIngestionUseCase`, `RagRetrievalPolicy`, and explicit no-result/source-unavailable outcomes.
- Configure PostgreSQL with pgvector using `pgvector/pgvector:pg17`.
- Implement an Ollama embedding outbound adapter through Spring AI, with Spring AI types confined to adapter/configuration. Embedding model name and dimension are configured, not hardcoded in business logic.
- Use the documented `nomic-embed-text` prefixes for chunks and questions if retained in local RAG skill guidance; otherwise update that guidance before implementation.
- Implement product-page fetch and extraction for the configured default CDQ Fraud Guard URL. Treat extracted text as untrusted input.
- Normalize, chunk, embed, and store chunks with deterministic metadata.
- Emit an ingestion report with source URL, content hash, chunk count, and skipped or replaced content.
- Expose a documented ingestion entry point, such as a CLI command or local inbound adapter, runnable without chat orchestration.
- Implement semantic retrieval with top-k and relevance threshold configuration.
- Keep retrieved prompt context lean and preserve enough source metadata for answer grounding.
- Add mandatory Testcontainers tests on `pgvector/pgvector:pg17` for schema initialization, `vector(768)` insertion, similarity retrieval, and idempotent re-ingestion.
- Add contract tests for chunk row shape: text, embedding, source URL, content hash, chunk index, and ingestion timestamp.
- Add tests for representative CDQ fixture text, relevant retrieval, off-topic no-result retrieval, and extraction/embedding source-unavailable outcomes.
- Document ingestion commands and expected local database setup.

### Expected Files and Modules

- `assistant-app/` RAG ingestion and retrieval use cases.
- `assistant-app/` pgvector adapter.
- Database migration or schema initialization files.
- Testcontainers configuration.
- README RAG setup and ingestion section.

### Acceptance Criteria

- `./mvnw -pl assistant-app test` passes for RAG tests and the actual output is recorded.
- Clean-database ingestion stores retrievable chunks with `vector(768)` embeddings and full metadata.
- Re-running ingestion replaces the prior chunk set without duplicates; unchanged content hash skips re-embedding or records an unchanged outcome.
- A representative CDQ fixture question retrieves expected `KnowledgeSnippet` values under configured top-k and threshold.
- An off-topic question returns explicit insufficient-product-knowledge without lowering the threshold to force a match.
- CDQ extraction or embedding failures surface source-unavailable outcomes; tests do not fabricate product knowledge.
- README documents Docker/pgvector setup, `ollama pull nomic-embed-text`, and the ingestion command.

### Suggested Commit Message

`feat: add pgvector rag ingestion`

## Phase 5: Assistant Orchestration

### Goal

Implement the application service that routes user questions to required sources, composes grounded answers, and calls Ollama only with explicit context.

### Prerequisites

- Phase 3 countries and weather ports/adapters pass.
- Phase 4 `RagKnowledgePort` and retrieval behavior pass.
- Ollama model `qwen3:4b` is available locally for live checks or replaced by controlled `LlmPort` implementations in automated tests.

### Tasks

- Define `UserQuestion`, `AssistantAnswer`, `AnswerSource`, `ToolExecutionResult`, and request-local `ConversationTurn`.
- Define `ConversationTurn` as single-request audit data only. Do not persist it and do not use it to answer later requests.
- Implement deterministic `SourceRoutingPolicy` or equivalent application-code rules for country-only, weather-only, combined country-weather, CDQ product, and Berlin place-plus-synthesis routes. The model must not choose sources or tools.
- Implement `AnswerQuestionUseCase` or `AssistantApplicationService`.
- Implement `ResponseComposer` so verified tool/RAG facts and general model synthesis are distinct in `AssistantAnswer`.
- Integrate Spring AI Ollama adapter behind `LlmPort`.
- Keep `LlmPort` as the central AI gateway so Spring AI and Ollama details do not leak into orchestration or domain concepts.
- Do not introduce an autonomous tool loop. Any optional tool registry is wiring-only and must not make routing decisions.
- Ensure source-unavailable behavior prevents fabricated facts.
- Emit structured request logs or trace records with correlation id, selected route, invoked ports, RAG retrieval count, and source-unavailable outcomes.
- Add orchestration tests using controlled ports.

### Expected Files and Modules

- `assistant-app/` question, orchestration, RAG, tools, and LLM packages.
- `assistant-app/` orchestration tests.
- README configuration section for Ollama model and base URL.

### Acceptance Criteria

- `./mvnw -pl assistant-app test` passes for orchestration tests and the actual output is recorded.
- Controlled-port tests prove routing for the required demo questions:
  - "What is the capital city of Germany?" calls countries only and does not require `LlmPort` for factual fallback.
  - "What is the temperature currently in Munich?" calls weather only.
  - "What is the temperature of the capital of Germany currently?" calls countries before weather.
  - "What do you know about Berlin?" calls countries and `LlmPort` only; weather and RAG do not fire.
- Berlin answer states Berlin is the capital of Germany from country facts, does not present unsourced specifics as verified facts, and does not label model synthesis as a tool result.
- If countries fails in the combined path, weather is not called and no capital or temperature is invented.
- If countries succeeds and weather fails in the combined path, the answer is partial, names weather as unavailable, and includes only grounded country facts.
- A CDQ product question retrieves RAG knowledge and passes compact grounded snippets to `LlmPort`; a no-result path reports insufficient product knowledge without model-memory fallback.
- When synthesis is required and Ollama is unavailable, the response names the model source unavailable and does not fabricate missing facts.
- `LlmPort` prompts contain only grounded source results plus compact instructions; no raw page dumps or upstream JSON payloads.
- `ResponseComposer` tests verify source labeling and synthesis-vs-tool-result distinction.
- No Spring AI, MCP SDK, JDBC, or HTTP client types appear in domain or application orchestration code.

### Suggested Commit Message

`feat: orchestrate grounded assistant answers`

## Phase 6: Chat Interface

### Goal

Provide a local chat interface for reviewers to ask questions and see assistant answers.

### Prerequisites

- Phase 5 orchestration is callable from application code with controlled ports.

### Tasks

- Implement Assistant API endpoint for chat requests.
- Implement typed `ChatRequest` and `ChatResponse` DTOs that map to and from `UserQuestion` and `AssistantAnswer`.
- Keep the controller thin: parse the request, call one application service, and map the result. No routing, source selection, or port decisions in the controller.
- Implement a simple single-turn local Chat Interface as a separate Astro frontend (`chat-ui/`). Each request sends only the current question; no server-side chat history and no prior-turn payload.
- Keep `assistant-app` API-only (JSON REST). Do not serve HTML from Spring; the Chat Interface calls the Assistant API over HTTP with CORS configured for the local Astro dev origin.
- Display assistant responses, source metadata, and source-unavailable messages clearly in the Astro UI.
- Avoid exposing stack traces, secrets, or local environment details in normal responses.
- Define HTTP mapping for validation failures, source-unavailable responses, and unexpected failures.
- Add Assistant API contract tests and at least one HTTP chat-path integration test with controlled ports.
- Update README run instructions.

### Expected Files and Modules

- `assistant-app/` inbound HTTP adapter (JSON only).
- `chat-ui/` Astro Chat Interface (separate frontend module).
- Chat API tests in `assistant-app`.
- README run section (backend + `chat-ui` startup).

### Acceptance Criteria

- A reviewer can start the assistant locally with documented commands.
- A reviewer can submit a natural-language question through the chat interface.
- Each chat request is independent; no server-side chat history is stored or used.
- The interface displays source-unavailable messages and, for successful answers, visible source attribution for countries, weather, RAG, and model synthesis as applicable.
- Weather answers shown in the interface include location, temperature, and timestamp labeled as observed time or retrieval time.
- The chat controller contains no routing or port-level decisions.
- Automated tests cover Assistant API request/response contract and at least one HTTP chat path using controlled ports. Scripted-only verification is allowed only with an explicit documented blocker.

### Suggested Commit Message

`feat: add local chat interface`

## Phase 7: Tests and Demo Answers

### Goal

Verify the implementation against acceptance criteria and capture final demo answers from the running assistant.

### Prerequisites

- Phases 2 through 6 exit gates have passed. Phase 7 does not replace tests that belong with implemented behavior.
- Structured request logs or trace records from Phase 5 are available for capture.

### Tasks

- Implement black-box E2E checks in `e2e-tests/` or an equivalent scripted verifier against the running `assistant-app` HTTP chat API.
- Run focused tests for countries, weather, RAG, orchestration, and chat and save actual output in demo evidence.
- Run the assistant locally with required dependencies.
- Ask required demo questions against the running assistant.
- Ask at least one additional showcase question, including a CDQ Fraud Guard product question.
- Stop one required source during demo capture and save the assistant's source-unavailable response with matching trace evidence.
- Save final demo answers with source path evidence.
- Save request trace evidence showing correlation id, selected route or sources, tool calls, RAG retrieval count including `0`, and source-unavailable outcomes.
- Document unavailable external services honestly if any dependency cannot run.

### Expected Files and Modules

- `e2e-tests/`
- `docs/demo/final-answers.md`
- `docs/demo/demo-run-log.md`
- `docs/demo/request-traces/`

### Acceptance Criteria

- Required demo answers are captured only from the running assistant.
- At least one showcase answer is captured only from the running assistant.
- `docs/demo/final-answers.md` records demo run timestamp, configuration summary without secrets, each question, copied answer, source path, weather location/temperature/timestamp where applicable, and trace references.
- For "What do you know about Berlin?", trace evidence shows countries and LLM only; weather and RAG are absent; answer distinguishes country facts from model synthesis.
- For "What is the temperature of the capital of Germany currently?", answer and trace show countries before weather and name the resolved capital location.
- `docs/demo/request-traces/` contains one trace excerpt per required question and excludes secrets, local paths, and raw stack traces.
- `docs/demo/demo-run-log.md` includes startup commands, MCP configuration summary, ingestion command and result, ingestion summary, test commands with pasted actual output, trace-capture method, and skipped verification notes.
- E2E checks assert routing and trace fields, not exact volatile weather values.
- Tests cover happy paths and source-unavailable paths.
- Any skipped live verification records the failed source, the failed command or dependency, the assistant source-unavailable response, and whether the skip blocks an acceptance criterion. Missing values are not replaced with model memory or manual lookup.

### Suggested Commit Message

`test: capture assistant demo verification`

## Phase 8: README Finalization and AI Usage Report

### Goal

Make the submission easy to review and honest about AI assistance, limitations, setup, and verification evidence.

### Tasks

- Finalize README overview, prerequisites, setup, run, test, demo, AI usage, and limitations sections.
- Document Java, Docker/pgvector, Ollama `qwen3:4b`, Ollama `nomic-embed-text`, local MCP servers, weather configuration, and RAG ingestion prerequisites.
- Complete phase-scoped AI usage entries under `docs/ai/` for material implementation work.
- Add a README AI usage summary that distinguishes AI-assisted authoring from runtime-captured demo evidence.
- Link architecture docs, ADRs, test strategy, demo evidence, and known limitations.
- Verify documented build, test, run, and ingestion commands from a clean checkout. Document explicit blockers as limitations instead of implying success.

### Expected Files and Modules

- `README.md`
- `docs/ai/`
- `docs/spec/`
- `docs/adr/`
- Demo evidence files.

### Acceptance Criteria

- README commands match the implemented project and are backed by captured command output or an explicitly documented limitation.
- README demo section links to captured evidence and states honestly whether required, showcase, and source-unavailable demos succeeded or were skipped.
- AI usage documentation records task, tool or agent used, files changed, human review, and verification evidence per material change.
- README summary states that demo answers and request traces were captured from the running assistant, not generated by an agent.
- Limitations and unfulfilled tasks are stated plainly, including missing dependencies, skipped verification, and acceptance criteria not met.
- No spec, README, AI usage, or demo file contains fabricated final demo answers, test output, or trace content.

### Suggested Commit Message

`docs: finalize local assistant submission`
