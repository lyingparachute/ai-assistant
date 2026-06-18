# Local Java AI Assistant

Local-first AI Assistant: an Astro Chat Interface calls a Spring Boot API that combines Ollama synthesis, RAG knowledge over CDQ Fraud Guard product content in pgvector, and MCP-backed country and weather knowledge sources.

## Architecture

The backend follows Hexagonal Architecture as described in [docs/spec/05-architecture.md](docs/spec/05-architecture.md) and ADR `0005`. The Assistant API maps HTTP SSE requests into an application use case. The application depends on ports (`LlmPort`, `RagKnowledgePort`, `CountriesPort`, `WeatherPort`), while adapters hide Ollama, pgvector, REST Countries, WeatherAPI, and MCP runtime details. The Chat Interface talks to `POST /api/chat` over Server-Sent Events; the terminal `final` event is the authoritative answer.

Modules:

- `assistant-app` - API, orchestration, RAG, MCP client adapters
- `chat-ui` - Astro Chat Interface
- `countries-mcp-server` - REST Countries MCP server
- `e2e-tests` - black-box checks against a running assistant

## Tech Stack

| Component | Version / setting | Source |
| --- | --- | --- |
| Java | 21 | `pom.xml` |
| Spring Boot | 3.5.15 | `pom.xml` |
| Spring AI | 1.1.8 | `pom.xml` |
| MCP SDK | 1.0.2 | `pom.xml` |
| jsoup | 1.22.2 | `pom.xml` |
| Astro | 6.4.7 | `chat-ui/package.json` |
| Node.js | `>=22.12.0` | `chat-ui/package.json` |
| pgvector | `pgvector/pgvector:pg17` | README setup and ADR `0003` |
| Ollama chat model | `qwen3:4b` | `assistant-app/src/main/resources/application.yml` |
| Ollama embedding model | `nomic-embed-text` | `assistant-app/src/main/resources/application.yml` |
| REST Countries | v5 base URL | ADR `0008`, `application.yml` |
| Weather | `semdin/mcp-weather` plus WeatherAPI.com | `scripts/mcp-weather`, `application.yml` |

Spring Boot 4, Spring AI 2, and MCP SDK 2 are intentionally deferred in [docs/plans/backend-hygiene.md](docs/plans/backend-hygiene.md).

## Prerequisites

- Java 21. The repository uses Maven Wrapper (`./mvnw`), so a separate Maven install is not required.
- Node.js `>=22.12.0` with `npm`.
- Docker with port `5432` free for `pgvector/pgvector:pg17`.
- `git`, `lsof`, `curl`, and `python3`.
- [Ollama](https://ollama.com/) running at `http://localhost:11434`.
- Ollama models: `qwen3:4b` for answer synthesis and `nomic-embed-text` for RAG embeddings.
- Live country facts: `REST_COUNTRIES_API_KEY`, a free REST Countries v5 key.
- Live weather: `WEATHER_API_KEY` and `WEATHER_API_URL`; use `https://api.weatherapi.com/v1/current.json`.
- Free local ports: `8080` for the Assistant API, `4321` for the Chat Interface, and `5432` for pgvector.

Copy the local secret template and fill in your own keys. `.env` is gitignored.

```bash
cp .env.example .env
# edit .env: REST_COUNTRIES_API_KEY, WEATHER_API_KEY, WEATHER_API_URL
```

Without a key, the matching source returns a source-unavailable answer instead of fabricated facts.

Start Ollama and pull the required models:

```bash
ollama serve
ollama pull qwen3:4b
ollama pull nomic-embed-text
ollama list
```

If `ollama serve` says the address is already in use, verify the running service:

```bash
curl -sf http://localhost:11434/api/tags
```

## Setup

Start pgvector:

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

Build the countries MCP server jar:

```bash
./mvnw -pl countries-mcp-server -am package -DskipTests
```

Ingest CDQ Fraud Guard product content into pgvector (requires Ollama with `nomic-embed-text`, pgvector running, and network access to the CDQ product page):

```bash
ASSISTANT_INGEST_RAG=true ./mvnw -pl assistant-app spring-boot:run
```

Alternative using a Spring Boot program argument:

```bash
./mvnw -pl assistant-app spring-boot:run -Dspring-boot.run.arguments=--ingest-rag
```

On success, stdout reports `source-url`, `content-hash`, `chunk-count`, and `outcome`. Re-run is idempotent: unchanged page content is skipped; changed content replaces the prior chunk set.

## Run

From the repository root:

```bash
./scripts/start-assistant.sh
```

macOS: double-click `scripts/start-assistant.command` in Finder.

The script starts the backend on `http://localhost:8080` and the Chat Interface on `http://localhost:4321`. Logs are written under `.local/logs/`. Stop with Ctrl+C.

Manual run:

```bash
./mvnw -pl countries-mcp-server -am package -DskipTests
./mvnw -pl assistant-app spring-boot:run

cd chat-ui
npm install
npm run dev
```

API contract: [docs/spec/14-assistant-api-contract.md](docs/spec/14-assistant-api-contract.md). `POST /api/chat` returns `text/event-stream`: `trace` events show Source-Usage Trace entries, optional `token` events stream synthesis text, and the terminal `final` event contains the authoritative `ChatResponse`.

### Chat Interface behaviour

The Astro Chat Interface at `http://localhost:4321` is a browser-only workspace:

- **Session display** — prior questions and answers stay visible for the browser session. Refresh clears the display. Each API request still sends `{ question }` only; prior turns are not sent to the Assistant API (ADR `0006`).
- **Stop** — aborts the in-flight SSE stream. The turn shows “Stopped — incomplete” and keeps any partial Source-Usage Trace and Streamed Answer text visible.
- **Errors** — HTTP validation failures, connection failures, and mid-stream SSE `error` events each produce distinct in-thread states; the workspace does not go blank while turns are visible.
- **Demo question chips** — six chips load from the same JSON file as e2e tests and demo capture ([e2e-tests/src/test/resources/demo-questions.json](e2e-tests/src/test/resources/demo-questions.json)). Clicking a chip fills the composer; it does not auto-submit.
- **Client abort note** — when you press Stop or navigate away, the browser closes the SSE connection but the backend worker may continue until its own timeout. Backend cancellation on client disconnect is a documented follow-up, not a guarantee today.

Manual SSE probe:

```bash
curl -sfN -X POST http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"question":"What is the capital city of Germany?"}'
```

## Tests

```bash
./mvnw test
./mvnw verify
./mvnw verify -P e2e
cd chat-ui && npm run build
```

`./mvnw verify -P e2e` requires a running assistant on `http://localhost:8080` and fails when no assistant responds. Without `-P e2e`, live demo verification does not run.

Fresh Phase 8 verification is recorded in [docs/demo/clean-checkout-verification.md](docs/demo/clean-checkout-verification.md).

## Demo

The required and showcase demo questions are defined once in [e2e-tests/src/test/resources/demo-questions.json](e2e-tests/src/test/resources/demo-questions.json). The Chat Interface demo chips, `scripts/capture-demo-answers.sh`, and `RequiredDemoQuestionsIT` all read that file.

| Key | Question | Expected source path |
| --- | --- | --- |
| `germany-capital` | What is the capital city of Germany? | Country Facts through the countries MCP server |
| `munich-weather` | What is the temperature currently in Munich? | Weather Observation through the weather MCP server |
| `germany-capital-weather` | What is the temperature of the capital of Germany currently? | Country Facts, then Weather Observation |
| `berlin-place` | What do you know about Berlin? | Country Facts plus labelled model synthesis |

Showcase questions cover CDQ Fraud Guard RAG knowledge and a no-route source-unavailable response (`Atlantis`). The live weather-key-unset unavailable path is test-covered only; it was not live-captured in the final demo evidence.

Captured demo evidence:

- [docs/demo/final-answers.md](docs/demo/final-answers.md)
- [docs/demo/demo-run-log.md](docs/demo/demo-run-log.md)
- [docs/demo/request-traces/](docs/demo/request-traces/)

To recapture demo answers from a running assistant:

```bash
./scripts/capture-demo-answers.sh
```

Raw capture output is written to `docs/demo/capture/`.

## AI Usage

AI-assisted work is documented under [docs/ai/](docs/ai/). Those entries record the task, agent role, files affected, human review status, and verification evidence for material work.

- AI helped draft plans, implement code, review changes, debug runtime blockers, and finalize documentation.
- Human direction constrained scope, selected trade-offs, required live verification, and rejected fabricated evidence.
- Demo Answers are not AI-authored documentation. They are captured from the running assistant and linked from [docs/demo/final-answers.md](docs/demo/final-answers.md).
- Runtime-captured evidence is separated from AI usage notes so generated text is not confused with verified behavior.
- Source-unavailable behavior is documented honestly: the Atlantis demo proves the no-route guard; weather credential outage is covered by automated tests and earlier run notes, not by the final live capture.

## Limitations

- No conversational memory across requests (ADR `0006`).
- Source routing is deterministic application logic; bounded agentic orchestration remains a proposed follow-up in [docs/plans/improve-agentic-tool-orchestration.md](docs/plans/improve-agentic-tool-orchestration.md).
- The application is local-only: no user accounts, no remote deployment, no cloud LLM providers.
- RAG knowledge is limited to CDQ Fraud Guard product-page content.
- External sources must be available for fully grounded live answers: REST Countries v5, WeatherAPI.com, Ollama, and local pgvector.
- Weather values are volatile. Demo weather answers include location and retrieval time in captured evidence.
- Historical upstream blockers from the first demo capture were resolved in later sessions; details remain in [docs/demo/demo-run-log.md](docs/demo/demo-run-log.md).

## Documentation

| Topic | Location |
| --- | --- |
| Domain language | [CONTEXT.md](CONTEXT.md) |
| Product specification | [docs/spec/01-product-specification.md](docs/spec/01-product-specification.md) |
| Non-functional requirements | [docs/spec/02-non-functional-requirements.md](docs/spec/02-non-functional-requirements.md) |
| Acceptance criteria | [docs/spec/03-acceptance-criteria.md](docs/spec/03-acceptance-criteria.md) |
| Architecture | [docs/spec/05-architecture.md](docs/spec/05-architecture.md) |
| Implementation plan | [docs/spec/06-implementation-plan.md](docs/spec/06-implementation-plan.md) |
| Test strategy | [docs/spec/07-test-strategy.md](docs/spec/07-test-strategy.md) |
| Demo plan | [docs/spec/08-demo-plan.md](docs/spec/08-demo-plan.md) |
| Assistant API contract | [docs/spec/14-assistant-api-contract.md](docs/spec/14-assistant-api-contract.md) |
| Demo evidence | [docs/demo/](docs/demo/) |
| AI-assisted work | [docs/ai/](docs/ai/) |
| ADR `0001` Spring AI | [docs/adr/0001-use-spring-ai.md](docs/adr/0001-use-spring-ai.md) |
| ADR `0002` Ollama model | [docs/adr/0002-use-ollama-qwen3-4b.md](docs/adr/0002-use-ollama-qwen3-4b.md) |
| ADR `0003` pgvector | [docs/adr/0003-use-pgvector-for-rag.md](docs/adr/0003-use-pgvector-for-rag.md) |
| ADR `0004` MCP | [docs/adr/0004-use-mcp-for-external-tools.md](docs/adr/0004-use-mcp-for-external-tools.md) |
| ADR `0005` Hexagonal Architecture | [docs/adr/0005-use-hexagonal-architecture.md](docs/adr/0005-use-hexagonal-architecture.md) |
| ADR `0006` no conversational memory | [docs/adr/0006-keep-conversation-memory-out-of-scope.md](docs/adr/0006-keep-conversation-memory-out-of-scope.md) |
| ADR `0007` embedding model | [docs/adr/0007-use-ollama-embedding-model.md](docs/adr/0007-use-ollama-embedding-model.md) |
| ADR `0008` REST Countries v5 | [docs/adr/0008-migrate-rest-countries-to-v5.md](docs/adr/0008-migrate-rest-countries-to-v5.md) |
| ADR `0009` SSE Assistant API | [docs/adr/0009-stream-assistant-api-over-sse.md](docs/adr/0009-stream-assistant-api-over-sse.md) |
