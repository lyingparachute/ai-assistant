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

## Phase 2: Countries MCP Server

### Goal

Build the custom MCP server that exposes country facts through REST Countries.

### Tasks

- Define country lookup tool inputs and outputs. The tool accepts a country name or a capital-city name, so "Germany" and "Berlin" both resolve to `CountryInfo` for Germany.
- Design MCP tool names, descriptions, and JSON schemas as semantic assistant-facing tools, not a REST Countries API mirror.
- Structure the MCP server around core server factory, tools, schemas, services, config, and typed error helpers.
- Implement REST Countries outbound adapter with configured base URL and timeout.
- Translate REST Countries responses into `CountryInfo`.
- Surface invalid-country and source-unavailable outcomes with recovery hints.
- Add graceful startup and shutdown behavior for local stdio execution.
- Add contract and integration-style tests with controlled REST Countries responses.
- Document how to run the countries MCP server locally.

### Expected Files and Modules

- `countries-mcp-server/`
- `shared-kernel/` only for stable shared result concepts, if needed.
- Test fixtures for REST Countries responses.
- README countries MCP setup section.

### Acceptance Criteria

- A country-capital query for Germany returns Berlin through the MCP server path.
- A capital-city query for Berlin resolves to Germany through the MCP server path.
- REST Countries failures are surfaced as source failures.
- Invalid-country behavior returns a useful recovery hint and is tested.
- Local MCP startup is documented with command, args, env, working directory, and timeout expectations.
- Tests do not require uncontrolled network access.

### Suggested Commit Message

`feat: add countries mcp server`

## Phase 3: Weather MCP Integration

### Goal

Integrate the assistant application with the local `semdin/mcp-weather` MCP server through an adapter-owned boundary.

### Tasks

- Define `WeatherPort` and `WeatherReport` in the assistant boundary.
- Implement MCP client adapter for current-weather lookup.
- Require weather reports to include location and a `WeatherTimestamp` that distinguishes an observed time from the source from the adapter's retrieval time.
- Translate weather MCP failures into source-unavailable outcomes.
- Add tests using a controlled MCP adapter or test server.
- Document weather MCP installation and local startup requirements.

### Expected Files and Modules

- `assistant-app/` weather port and adapter.
- `assistant-app/` weather tests.
- README weather MCP setup section.

### Acceptance Criteria

- A weather query for Munich requests current weather through the weather MCP path.
- Successful results include temperature, location, and a weather timestamp labeled as an observed time or a retrieval time.
- Weather MCP failures do not produce invented temperature values.
- Focused tests cover success and source-unavailable behavior.

### Suggested Commit Message

`feat: integrate weather mcp tool`

## Phase 4: pgvector and RAG Ingestion

### Goal

Create repeatable ingestion and retrieval for CDQ Fraud Guard product-page RAG knowledge.

### Tasks

- Configure PostgreSQL with pgvector using `pgvector/pgvector:pg17`.
- Implement product-page text extraction for the configured CDQ URL.
- Normalize, chunk, embed, and store chunks with deterministic metadata.
- Save optional local ingestion artifacts for debugging, such as extracted text, chunk metadata, and ingestion report.
- Implement semantic retrieval with top-k and relevance threshold configuration.
- Keep retrieved prompt context lean and preserve enough source metadata for answer grounding.
- Add Testcontainers-based tests for pgvector storage and retrieval where practical.
- Add tests for representative product text and no-result retrieval.
- Document ingestion commands and expected local database setup.

### Expected Files and Modules

- `assistant-app/` RAG ingestion and retrieval use cases.
- `assistant-app/` pgvector adapter.
- Database migration or schema initialization files.
- Testcontainers configuration.
- README RAG setup and ingestion section.

### Acceptance Criteria

- Re-running ingestion on a clean local database produces retrievable chunks.
- Product questions retrieve relevant `KnowledgeSnippet` values.
- No-result retrieval returns an explicit insufficient-product-knowledge outcome.
- Tests cover ingestion, retrieval, and no-result behavior.

### Suggested Commit Message

`feat: add pgvector rag ingestion`

## Phase 5: Assistant Orchestration

### Goal

Implement the application service that routes user questions to required sources, composes grounded answers, and calls Ollama only with explicit context.

### Tasks

- Define `UserQuestion`, `AssistantAnswer`, `AnswerSource`, `ToolExecutionResult`, and request-local `ConversationTurn`.
- Implement routing for country, weather, combined country-weather, RAG, and general synthesis paths.
- Implement `ResponseComposer`.
- Integrate Spring AI Ollama adapter behind `LlmPort`.
- Keep `LlmPort` as the central AI gateway so Spring AI and Ollama details do not leak into orchestration or domain concepts.
- Add an infrastructure-edge tool registry only if it reduces MCP wiring duplication; required demo routing remains deterministic.
- Ensure source-unavailable behavior prevents fabricated facts.
- Add orchestration tests using controlled ports.

### Expected Files and Modules

- `assistant-app/` question, orchestration, RAG, tools, and LLM packages.
- `assistant-app/` orchestration tests.
- README configuration section for Ollama model and base URL.

### Acceptance Criteria

- Required demo-question paths choose the correct ports.
- The combined Germany-capital-weather path calls countries before weather.
- Source-unavailable outcomes name the failed source and do not call model memory as a replacement.
- Model prompts receive only grounded source results and compact instructions needed for the answer.
- Any tool-execution loop has max turns, timeout, cancellation, and typed tool-result limits before it can be used.
- Focused tests verify happy paths and failure paths.

### Suggested Commit Message

`feat: orchestrate grounded assistant answers`

## Phase 6: Chat UI

### Goal

Provide a local chat interface for reviewers to ask questions and see assistant answers.

### Tasks

- Implement Assistant API endpoint for chat requests.
- Implement a simple local Chat UI.
- Display assistant responses and source-unavailable messages clearly.
- Avoid exposing stack traces, secrets, or local environment details in normal responses.
- Add a chat-path test or repeatable verification.
- Update README run instructions.

### Expected Files and Modules

- `assistant-app/` inbound HTTP adapter.
- Chat UI assets or server-rendered page.
- Chat API tests.
- README run section.

### Acceptance Criteria

- A reviewer can start the assistant locally with documented commands.
- A reviewer can submit a natural-language question through the chat interface.
- The UI displays answers and clear source-unavailable messages.
- The chat path has automated or scripted verification.

### Suggested Commit Message

`feat: add local chat interface`

## Phase 7: Tests and Demo Answers

### Goal

Verify the implementation against acceptance criteria and capture final demo answers from the running assistant.

### Tasks

- Add E2E tests or scripts for required demo questions.
- Run focused tests for countries, weather, RAG, orchestration, and chat.
- Run the assistant locally with required dependencies.
- Ask required demo questions against the running assistant.
- Save final demo answers with source path evidence.
- Save request trace evidence showing selected sources, tool calls, RAG retrieval count, and source-unavailable outcomes.
- Document unavailable external services honestly if any dependency cannot run.

### Expected Files and Modules

- `e2e-tests/`
- `docs/demo/` or another documented demo evidence location.
- README test and demo sections.

### Acceptance Criteria

- Required demo answers are captured only from the running assistant.
- Demo evidence shows which source path each answer exercised.
- Demo evidence includes enough trace or log output to prove the final text was not the only verified artifact.
- Tests cover happy paths and source-unavailable paths.
- Any skipped external verification has an explicit documented reason.

### Suggested Commit Message

`test: capture assistant demo verification`

## Phase 8: README Finalization and AI Usage Report

### Goal

Make the submission easy to review and honest about AI assistance, limitations, setup, and verification evidence.

### Tasks

- Finalize README overview, prerequisites, setup, run, test, demo, and limitations sections.
- Add AI usage report under `docs/ai/`.
- Link architecture docs, ADRs, test strategy, demo evidence, and known limitations.
- Verify commands from a clean checkout where practical.

### Expected Files and Modules

- `README.md`
- `docs/ai/`
- `docs/spec/`
- `docs/adr/`
- Demo evidence files.

### Acceptance Criteria

- README commands match the implemented project.
- AI usage documentation records task, tool or agent used, files changed, human review, and verification evidence.
- Limitations and unfulfilled tasks are stated plainly.
- Final documentation does not claim unverified passing behavior.

### Suggested Commit Message

`docs: finalize local assistant submission`
