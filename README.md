# Local Java AI Assistant

## Overview

This repository contains a recruitment-task implementation of a local AI Assistant built with Java frameworks. The assistant will provide a chat interface backed by a local Ollama model, retrieval-augmented generation over CDQ Fraud Guard product knowledge, a local pgvector database, and MCP tool integrations.

The implementation is intentionally local-first. Required runtime dependencies are expected to run on the developer machine, including Ollama with the assignment model, PostgreSQL with pgvector, the custom REST Countries MCP server, and the local weather MCP server.

Phase 1 has added the minimal Java/Spring multi-module skeleton. Assistant behavior is not implemented yet.

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

The build itself does not require Docker, Ollama, REST Countries, pgvector, or weather configuration yet.

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

- `assistant-app`: Spring Boot application shell only.
- `countries-mcp-server`: Spring Boot application shell only.
- `e2e-tests`: placeholder black-box test module for later demo-path verification.

`shared-kernel` is not present because no concrete cross-module Java type is required yet.

## Running the Assistant

Runtime behavior is pending later implementation phases. The application entry points compile, but they do not expose a chat interface, implement MCP tools, call Ollama, query pgvector, or produce assistant answers yet.

The final service must start locally and expose a chat interface that can answer the required demo questions using the correct source:

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

Current tests are skeleton smoke tests only. Later phases must add focused automated tests for assistant behavior, RAG ingestion and retrieval, MCP integrations, error handling, and demo-question paths.

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
- The Phase 1 skeleton has no assistant behavior beyond Spring Boot bootstrapping.
- `.mcp.json` records a local MCP configuration convention only; the countries MCP tool behavior is not implemented yet.
