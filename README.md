# Local Java AI Assistant

## Overview

This repository contains a recruitment-task implementation of a local AI Assistant built with Java frameworks. The assistant will provide a chat interface backed by a local Ollama model, retrieval-augmented generation over CDQ Fraud Guard product knowledge, a local pgvector database, and MCP tool integrations.

The implementation is intentionally local-first. Required runtime dependencies are expected to run on the developer machine, including Ollama with the assignment model, PostgreSQL with pgvector, the custom REST Countries MCP server, and the local weather MCP server.

Phase 1 added the minimal Java/Spring multi-module skeleton. Phase 2 added the custom countries MCP server backed by REST Countries. Phase 3 added assistant-side MCP client adapters for countries and weather behind `CountriesPort` and `WeatherPort`. Phase 4 added CDQ Fraud Guard RAG ingestion and retrieval over pgvector behind `RagKnowledgePort`, with a documented local ingestion entry point. Phase 5 added deterministic source routing and `AnswerQuestionUseCase` orchestration behind `LlmPort`, composing grounded answers with explicit source labeling.

## Architecture

The planned architecture follows a hexagonal style:

- The chat interface is an inbound adapter.
- Application services orchestrate assistant requests, RAG retrieval, and tool calls.
- Domain ports describe required capabilities such as country lookup, weather lookup, document retrieval, and model completion.
- Infrastructure adapters integrate with Ollama, pgvector, REST Countries, and MCP servers.
- Tests verify behavior at boundaries with real or controlled local dependencies where practical.

Detailed architecture decisions are recorded under `docs/adr/`.

## Tech Stack

- Java 21 LTS.
- Maven Wrapper using Apache Maven 3.9.11.
- Spring Boot 3.5.9.
- Spring AI dependency management through Spring AI BOM 1.1.2. Spring AI runtime dependencies will be added only inside outbound adapters or configuration when those phases implement them.
- Ollama for local model execution.
- Assignment model: `qwen3:4b`, configured externally.
- Embedding model: `nomic-embed-text` (dimension 768), configured externally (ADR `0007`).
- PostgreSQL with pgvector using `pgvector/pgvector:pg17`.
- MCP for tool integrations.
- REST Countries API through a custom MCP server.
- Local weather MCP server from `semdin/mcp-weather`.
- JUnit Jupiter through Spring Boot test conventions.

## Prerequisites

Expected local tools for the current skeleton:

- Java 21 JDK.
- Network access for the first Maven Wrapper run so it can download Apache Maven and dependencies.

Expected local tools for later implementation phases:

- Docker or another local container runtime for pgvector.
- Ollama installed locally.
- The assignment Ollama model available locally.
- Network access during setup to retrieve CDQ Fraud Guard source text and REST Countries data.
- Local MCP weather server installed and runnable.

The build itself does not require Docker, Ollama, pgvector, or weather configuration yet. The countries MCP server needs network access only when it calls REST Countries at runtime.

## Countries MCP Server

Run the server from the repository root using the documented `.mcp.json` entry (`transport: stdio`, host `timeout: 60000` ms):

```bash
./mvnw -q -pl countries-mcp-server spring-boot:run
```

Configuration is externalized under `countries.mcp.*` (see `docs/spec/11-countries-mcp-tool-contract.md`). Defaults target REST Countries v3.1 at `https://restcountries.com/v3.1`.

Run focused countries MCP tests:

```bash
./mvnw -pl countries-mcp-server test
```

Manual smoke check (requires an MCP host or client that speaks stdio JSON-RPC):

1. Start the server with the command above from the repository root.
2. Call the `country_lookup` tool with `{"name":"Germany"}` and confirm `countryName`, `capital`, `region`, and `population` in the success envelope.
3. Call with `{"name":"Berlin"}` and confirm the same Germany facts through the capital-city path.
4. Stop the process with Ctrl+C and confirm it exits without hanging.

Automated tests use a stubbed REST Countries HTTP server and do not call the live API.

## Weather MCP Server

The assistant calls the local `semdin/mcp-weather` server through `WeatherMcpClientAdapter` behind `WeatherPort`. See `docs/spec/13-weather-mcp-tool-contract.md` for the tool name, input schema, and text response pattern.

Install and configure the weather server locally:

1. Clone or install [semdin/mcp-weather](https://github.com/semdin/mcp-weather) and ensure `mcp-weather` (or `npx tsx src/index.ts`) is on your PATH.
2. Set environment variables (do not commit secrets):
   - `WEATHER_API_KEY` — provider API key.
   - `WEATHER_API_URL` — provider URL (for example `https://api.weatherapi.com/v1/current.json`).
3. Use the `.mcp.json` `weather` entry (`transport: stdio`, `timeout: 60000` ms) or `assistant.mcp.weather.*` in `assistant-app/src/main/resources/application.yml`.

Run focused assistant MCP adapter tests:

```bash
./mvnw -pl assistant-app test
```

Manual smoke check (optional, requires live weather API configuration):

1. Export `WEATHER_API_KEY` and `WEATHER_API_URL`.
2. Start `mcp-weather` from your MCP host or call `get-weather` with `{"city":"Munich"}`.
3. Confirm the text response matches `the weather in Munich is currently: <temperature>`.

Automated tests stub MCP responses and do not call the live weather API.

## RAG over pgvector (CDQ Fraud Guard)

Phase 4 ingests CDQ Fraud Guard product-page content into PostgreSQL with pgvector and retrieves `KnowledgeSnippet` values behind `RagKnowledgePort`. Chat orchestration that answers product questions is still in a later phase; ingestion and retrieval are runnable and tested now.

### PostgreSQL with pgvector

Start a local database matching `assistant.rag.*` defaults in `assistant-app/src/main/resources/application.yml`:

```bash
docker run -d --name assistant-pgvector \
  -e POSTGRES_DB=assistant_rag \
  -e POSTGRES_USER=assistant \
  -e POSTGRES_PASSWORD=assistant \
  -p 5432:5432 \
  pgvector/pgvector:pg17
```

Defaults: JDBC URL `jdbc:postgresql://localhost:5432/assistant_rag`, user `assistant`, password `assistant`.

### Ollama embedding model

Pull the assignment embedding model (dimension 768, ADR `0007`):

```bash
ollama pull nomic-embed-text
```

Ollama must be running at the configured base URL (`assistant.embedding.ollama-base-url`, default `http://localhost:11434`).

### Ingest product knowledge

From the repository root, with pgvector and Ollama available:

```bash
./mvnw -pl assistant-app spring-boot:run -- --ingest-rag
```

Or:

```bash
ASSISTANT_INGEST_RAG=true ./mvnw -pl assistant-app spring-boot:run
```

Ingestion fetches the configured CDQ Fraud Guard source URL (`assistant.rag.source-url`), extracts plain text, chunks it deterministically, embeds with `nomic-embed-text`, and stores rows in `rag_chunks`. Re-running replaces changed content or skips unchanged content by content hash.

### RAG-focused tests

```bash
./mvnw -pl assistant-app test
```

RAG integration tests use Testcontainers (`pgvector/pgvector:pg17`) and a deterministic test embedding adapter; they do not call the live CDQ website or Ollama during `./mvnw test`.

## Ollama chat model (assistant synthesis)

Pull the assignment chat model (ADR `0002`):

```bash
ollama pull qwen3:4b
```

Ollama must be running at the configured base URL (`assistant.llm.ollama-base-url`, default `http://localhost:11434`). Chat completion is wired behind `LlmPort` in `OllamaLlmAdapter`; orchestration calls it only when synthesis is required (for example Berlin place questions or CDQ product answers after RAG retrieval).

Configuration in `assistant-app/src/main/resources/application.yml`:

```yaml
assistant:
  llm:
    ollama-base-url: http://localhost:11434
    model-name: qwen3:4b
    timeout-seconds: 120
```

Orchestration tests use `StubLlmPort` and do not call live Ollama during `./mvnw test`.

## Local Setup

Current repository state:

1. Review `CONTEXT.md`.
2. Review the requirement documents under `docs/spec/`.
3. Confirm architecture decisions under `docs/adr/` before implementing production source code.
4. Run the wrapper-based build or tests:

```bash
./mvnw verify
./mvnw test
```

The repository contains these modules:

- `assistant-app`: Spring Boot application with countries and weather MCP client adapters, RAG ingestion and retrieval over pgvector, and Phase 5 orchestration (`AnswerQuestionUseCase`) behind application ports.
- `countries-mcp-server`: custom MCP server exposing the `country_lookup` tool over REST Countries v3.1.
- `e2e-tests`: placeholder black-box test module for later demo-path verification.

`shared-kernel` is not present because no concrete cross-module Java type is required yet.

## Running the Assistant

Phase 5 orchestration is implemented in application code (`AnswerQuestionUseCase`) but there is no HTTP chat interface yet (Phase 6). The countries MCP server is runnable locally; the assistant application supports RAG ingestion via `--ingest-rag`. Answering demo questions through a chat UI requires Phase 6.

When a chat interface is available, the assistant will route required demo questions using deterministic source routing and compose answers from:

- country facts through the REST Countries MCP server;
- weather facts through the local weather MCP server;
- CDQ Fraud Guard product knowledge through RAG over pgvector;
- general synthesis through the local Ollama model.

## Running Tests

Run all current tests:

```bash
./mvnw test
```

Run the current build verification:

```bash
./mvnw verify
```

Current tests include the Phase 1 skeleton smoke tests, Phase 2 countries MCP contract and integration tests, Phase 3 assistant MCP adapter contract and integration tests, Phase 4 RAG domain, pgvector, ingestion, and retrieval tests, and Phase 5 orchestration tests with controlled ports. Later phases must add HTTP chat-path integration tests and demo-question end-to-end verification.

## Demo Questions

Required demo questions:

- What is the capital city of Germany?
- What is the temperature currently in Munich?
- What is the temperature of the capital of Germany currently?
- What do you know about Berlin?

Additional showcase questions will be added after the assistant capabilities are implemented. Final runtime answers must be captured from the running assistant and must not be invented in documentation.

## AI Usage

AI assistance is allowed by the assignment. AI-assisted work is documented under `docs/ai/` with the prompt or task summary, agent role, files affected, human review performed, and verification evidence.

## Limitations

- Long-term and short-term memory are out of scope.
- The assistant must not fabricate unavailable tool or RAG results.
- Runtime answers are not included yet because the assistant has not been implemented.
- Phase 3 delivers countries and weather MCP client adapters in `assistant-app`; assistant orchestration and chat remain in later phases.
- Phase 4 delivers RAG ingestion and retrieval over pgvector; demo answers and chat UI remain in later phases.
- Phase 5 delivers deterministic orchestration and `ResponseComposer` source labeling; chat inbound adapter and demo capture remain in Phases 6–7.
- `.mcp.json` launches the countries MCP server over documented stdio transport; weather requires local `mcp-weather` installation and API configuration.
