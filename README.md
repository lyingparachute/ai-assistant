# Local Java AI Assistant

Local-first AI assistant: Astro Chat Interface, Spring Boot API, Ollama synthesis, RAG over CDQ Fraud Guard product knowledge (pgvector), and MCP tools for countries and weather.

## Prerequisites

- Java 21, Node.js ≥ 22.12, Docker (for pgvector)
- [Ollama](https://ollama.com/) with `qwen3:4b` and `nomic-embed-text`
- Live country facts: `REST_COUNTRIES_API_KEY` — a free [REST Countries](https://restcountries.com/) v5 key (500 req/month). REST Countries v3.1 is deprecated; the server targets v5 (ADR `0008`).
- Live weather: `WEATHER_API_KEY` and `WEATHER_API_URL` — a free [WeatherAPI.com](https://www.weatherapi.com/) key (for example `https://api.weatherapi.com/v1/current.json`)

### Local secrets

Copy the template and fill in your own keys; `.env` is gitignored and never committed.
`scripts/start-assistant.sh` sources it on startup so keys reach the assistant and the MCP
subprocesses.

```bash
cp .env.example .env
# edit .env: REST_COUNTRIES_API_KEY, WEATHER_API_KEY, WEATHER_API_URL
```

Without a key, the matching source returns a source-unavailable answer (no fabricated facts).

```bash
ollama pull qwen3:4b
ollama pull nomic-embed-text
```

## Quick Start

From the repository root:

```bash
./scripts/start-assistant.sh
```

macOS: double-click `scripts/start-assistant.command` in Finder.

Starts the backend on `http://localhost:8080` and the Chat Interface on `http://localhost:4321`. Logs: `.local/logs/`. Stop with **Ctrl+C**.

The script builds the countries MCP jar and installs Chat Interface dependencies on first run. It warns when Ollama, pgvector, or weather credentials are missing but still starts the stack.

### RAG database and ingestion

```bash
docker run -d --name assistant-pgvector \
  -e POSTGRES_DB=assistant_rag -e POSTGRES_USER=assistant -e POSTGRES_PASSWORD=assistant \
  -p 5432:5432 pgvector/pgvector:pg17

./mvnw -pl assistant-app spring-boot:run -- --ingest-rag
```

Stop a running assistant on port 8080 before ingestion — it uses a non-web context and exits after storing chunks.

### Manual run

```bash
./mvnw -pl countries-mcp-server -am package -DskipTests   # once
./mvnw -pl assistant-app spring-boot:run                  # terminal 1

cd chat-ui && npm install && npm run dev                  # terminal 2
```

API contract: `docs/spec/14-assistant-api-contract.md`.

## Tests

```bash
./mvnw test                    # all modules, no running server required
./mvnw verify                  # full build, no demo verification
./mvnw verify -P e2e           # demo verification against a live stack on port 8080
```

`./mvnw verify -P e2e` runs the demo verification (`RequiredDemoQuestionsIT`) against a running
assistant. It is opt-in: without `-P e2e` it never runs, and with `-P e2e` it fails (does not skip)
when no assistant responds on the configured base URL.

## Demo Questions

The required and showcase demo questions are defined once in
[`e2e-tests/src/test/resources/demo-questions.json`](e2e-tests/src/test/resources/demo-questions.json).
Both `scripts/capture-demo-answers.sh` and `RequiredDemoQuestionsIT` read that file.

Captured answers and traces: [docs/demo/final-answers.md](docs/demo/final-answers.md).

## Documentation

| Topic | Location |
| --- | --- |
| Domain language | `CONTEXT.md` |
| Requirements and architecture | `docs/spec/` |
| Decisions | `docs/adr/` |
| Demo evidence | `docs/demo/` |
| AI-assisted work | `docs/ai/` |

## Modules

- `assistant-app` — API, orchestration, RAG, MCP client adapters
- `chat-ui` — Astro Chat Interface
- `countries-mcp-server` — REST Countries MCP server
- `e2e-tests` — black-box checks against a running assistant

## Limitations

- No conversational memory across requests (ADR `0006`).
- The assistant does not fabricate tool or RAG results when a source is unavailable.
- Demo capture on 2026-06-16 hit upstream blockers (REST Countries v3.1 deprecated, weather key unset, RAG not ingested). Details: [docs/demo/demo-run-log.md](docs/demo/demo-run-log.md).
