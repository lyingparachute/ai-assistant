# Local Java AI Assistant

Local-first AI assistant: Astro Chat Interface, Spring Boot API, Ollama synthesis, RAG over CDQ Fraud Guard product knowledge (pgvector), and MCP tools for countries and weather.

## Prerequisites

- Java 21. The repository uses Maven Wrapper (`./mvnw`), so a separate Maven install is not required.
- Node.js `>=22.12.0` with `npm`. The Chat Interface and weather MCP bootstrap use Node tooling.
- Docker with port `5432` free for `pgvector/pgvector:pg17`.
- `git`, `lsof`, `curl`, and `python3`.
  - `scripts/start-assistant.sh` requires `lsof`.
  - `scripts/mcp-weather` uses `git`, `npm`, and `npx` on first run.
  - `scripts/capture-demo-answers.sh` requires `curl` and `python3`.
- [Ollama](https://ollama.com/) running at `http://localhost:11434`.
- Ollama models: `qwen3:4b` for answer synthesis and `nomic-embed-text` for RAG embeddings.
- Live country facts: `REST_COUNTRIES_API_KEY` — a free [REST Countries](https://restcountries.com/) v5 key (500 req/month). REST Countries v3.1 is deprecated; the server targets v5 (ADR `0008`).
- Live weather: `WEATHER_API_KEY` and `WEATHER_API_URL` — a free [WeatherAPI.com](https://www.weatherapi.com/) key. Use `https://api.weatherapi.com/v1/current.json` for the URL.
- Free local ports:
  - `8080` for the Assistant API, or set `ASSISTANT_BACKEND_PORT`.
  - `4321` for the Chat Interface, or set `CHAT_UI_PORT`.
  - `5432` for pgvector.

### Local secrets

Copy the template and fill in your own keys; `.env` is gitignored and never committed.
`scripts/start-assistant.sh` sources it on startup so keys reach the assistant and the MCP
subprocesses.

```bash
cp .env.example .env
# edit .env: REST_COUNTRIES_API_KEY, WEATHER_API_KEY, WEATHER_API_URL
```

Without a key, the matching source returns a source-unavailable answer (no fabricated facts).

### Ollama setup

Start Ollama in a separate terminal, or use the Ollama desktop app:

```bash
ollama serve
```

Then pull the required models:

```bash
ollama pull qwen3:4b
ollama pull nomic-embed-text
ollama list
```

If `ollama serve` says the address is already in use, Ollama is already running. Verify it with:

```bash
curl -sf http://localhost:11434/api/tags
```

## First Run

### 1. Start pgvector

```bash
docker run -d --name assistant-pgvector \
  -e POSTGRES_DB=assistant_rag \
  -e POSTGRES_USER=assistant \
  -e POSTGRES_PASSWORD=assistant \
  -p 5432:5432 \
  pgvector/pgvector:pg17
```

If the container already exists:

```bash
docker start assistant-pgvector
```

### 2. Ingest CDQ Fraud Guard RAG knowledge

Run ingestion after pgvector and Ollama are available. Stop any assistant already using port `8080` before this command; ingestion runs a non-web context and exits after storing chunks.

```bash
./mvnw -pl assistant-app spring-boot:run -- --ingest-rag
```

### 3. Start the assistant stack

From the repository root:

```bash
./scripts/start-assistant.sh
```

macOS: double-click `scripts/start-assistant.command` in Finder.

Starts the backend on `http://localhost:8080` and the Chat Interface on `http://localhost:4321`. Logs: `.local/logs/`. Stop with **Ctrl+C**.

The script builds the countries MCP jar and installs Chat Interface dependencies on first run. It warns when Ollama, pgvector, or weather credentials are missing but still starts the stack.

### 4. Open the Chat Interface

Open `http://localhost:4321` and ask the demo questions listed below.

### Manual run

Use this only if you do not want the launcher script:

```bash
./mvnw -pl countries-mcp-server -am package -DskipTests   # once
./mvnw -pl assistant-app spring-boot:run                  # terminal 1

cd chat-ui && npm install && npm run dev                  # terminal 2
```

API contract: `docs/spec/14-assistant-api-contract.md`. `POST /api/chat` returns
`text/event-stream` (Source-Usage Trace + Streamed Answer; the terminal `final` event is the
authoritative answer). Probe manually with:

```bash
curl -sfN -X POST http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"question":"What is the capital city of Germany?"}'
```

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

Current required questions:

| Key | Question | Expected source path |
| --- | --- | --- |
| `germany-capital` | What is the capital city of Germany? | Country Facts through the countries MCP server |
| `munich-weather` | What is the temperature currently in Munich? | Weather Observation through the weather MCP server |
| `germany-capital-weather` | What is the temperature of the capital of Germany currently? | Country Facts, then Weather Observation |
| `berlin-place` | What do you know about Berlin? | Country Facts plus labelled model synthesis |

Showcase questions cover CDQ Fraud Guard RAG knowledge and source-unavailable behavior.

Captured Demo Answers and Source-Usage Trace excerpts:

- [docs/demo/final-answers.md](docs/demo/final-answers.md)
- [docs/demo/demo-run-log.md](docs/demo/demo-run-log.md)
- [docs/demo/request-traces/](docs/demo/request-traces/)

To recapture demo answers from a running assistant:

```bash
./scripts/capture-demo-answers.sh
```

Raw capture output is written to `docs/demo/capture/`.

## Documentation

| Topic | Location |
| --- | --- |
| Domain language | `CONTEXT.md` |
| Requirements and architecture | `docs/spec/` |
| Decisions | `docs/adr/` |
| Demo evidence | `docs/demo/` |
| AI-assisted work | `docs/ai/` |
| Test strategy | `docs/spec/07-test-strategy.md` |
| Assistant API contract | `docs/spec/14-assistant-api-contract.md` |

## Modules

- `assistant-app` — API, orchestration, RAG, MCP client adapters
- `chat-ui` — Astro Chat Interface
- `countries-mcp-server` — REST Countries MCP server
- `e2e-tests` — black-box checks against a running assistant

## Architecture

The backend follows Hexagonal Architecture (ADR `0005`):

- The Assistant API receives questions and delegates to application use cases.
- Application use cases depend on ports: `LlmPort`, `RagKnowledgePort`, `CountriesPort`, and `WeatherPort`.
- Outbound adapters hide Ollama, pgvector, REST Countries, and MCP runtime details.
- The custom countries MCP server owns its REST Countries integration; `assistant-app` does not depend on its internals.
- The Chat Interface talks to the Assistant API over HTTP SSE (`POST /api/chat`).

## AI Usage

AI-assisted implementation and review notes are recorded under [docs/ai/](docs/ai/).
Those files document the task, agent role, files affected, human review, and verification evidence for material work.

Demo Answers are separate from AI usage notes: they are captured from the running assistant and stored under [docs/demo/](docs/demo/). They are not hand-written by an agent.

## Limitations

- No conversational memory across requests (ADR `0006`).
- The assistant does not fabricate tool or RAG results when a source is unavailable.
- External sources must be available for fully grounded live answers: REST Countries v5, WeatherAPI.com, Ollama, and local pgvector.
- Weather values are volatile. Demo weather answers include location and retrieval time in the captured evidence.
- The 2026-06-16 demo capture had upstream blockers; the 2026-06-17 capture resolved them and is documented in [docs/demo/demo-run-log.md](docs/demo/demo-run-log.md).

## Stack (Java backend)

| Component | Version |
| --- | --- |
| Java | 21 |
| Spring Boot | 3.5.15 |
| Spring AI | 1.1.8 |
| MCP SDK | 1.0.2 |
| Lombok | managed by Spring Boot parent (annotation processor required to compile) |
| Apache Commons Lang | 3.x (managed by Spring Boot parent) |

Spring Boot 4 / Spring AI 2 / MCP SDK 2 are intentionally deferred (platform migration).
