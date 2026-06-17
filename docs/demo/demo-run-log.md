# Demo Run Log

Capture session: **2026-06-16** (local machine)

## Dependencies started

```bash
# pgvector
docker run -d --name assistant-pgvector \
  -e POSTGRES_DB=assistant_rag \
  -e POSTGRES_USER=assistant \
  -e POSTGRES_PASSWORD=assistant \
  -p 5432:5432 \
  pgvector/pgvector:pg17

# Ollama (installed via Homebrew cask during this session)
ollama serve &
ollama pull nomic-embed-text
ollama pull qwen3:4b

# Countries MCP server jar (built once)
./mvnw -pl countries-mcp-server -am package -DskipTests
```

## MCP configuration used

| Server | Launch |
| --- | --- |
| Countries | `java -jar ../countries-mcp-server/target/countries-mcp-server-0.1.0-SNAPSHOT.jar` (via `assistant.mcp.countries` in `application.yml`) |
| Weather | `scripts/mcp-weather` (clones semdin/mcp-weather into `.local/semdin-mcp-weather` on first run) |

Weather subprocess env: `WEATHER_API_URL=https://api.weatherapi.com/v1/current.json` ( **`WEATHER_API_KEY` not set** ).

## RAG ingestion

Ingestion uses the `ingest-rag` profile (non-web context; MCP subprocesses are not started). Stop a running assistant on port 8080 before ingesting.

```bash
ASSISTANT_INGEST_RAG=true ./mvnw -pl assistant-app spring-boot:run
```

**Result:** Ingestion did not complete during capture. `rag_chunks` count remained **0**. CDQ showcase therefore returned `rag_knowledge: INSUFFICIENT`. Likely causes: assistant already bound to port 8080 during ingest attempt, or Ollama/pgvector unavailable at ingest time.

## Assistant startup

```bash
export WEATHER_API_URL=https://api.weatherapi.com/v1/current.json
./mvnw -pl countries-mcp-server -am package -DskipTests   # if jar missing
./mvnw -pl assistant-app spring-boot:run
```

Assistant API: `http://localhost:8080`

## Chat Interface startup

```bash
cd chat-ui && npm run dev
```

Chat Interface: `http://localhost:4321` (HTTP 200 verified on `/`)

## Demo capture command

```bash
./scripts/capture-demo-answers.sh
```

Output JSON: `docs/demo/capture/*.json`

## E2E tests (live stack)

Structural E2E checks assert JSON shape, source routing fields, and trace ids. They accept `USED` **or** `UNAVAILABLE` source statuses so the suite stays green when upstream APIs are blocked. This is **not** the same as demo acceptance (happy-path answers with live country, weather, and RAG data).

```bash
./mvnw -pl e2e-tests test
```

```
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Assistant unit/integration tests

```bash
./mvnw -pl assistant-app test
```

Note: requires `assistant_rag_test` database on local pgvector (`CREATE DATABASE assistant_rag_test;`).

## Upstream blockers recorded honestly

| Blocker | Impact |
| --- | --- |
| REST Countries v3.1 deprecated | All country-fact demos return `countries_facts: UNAVAILABLE` |
| `WEATHER_API_KEY` not configured | Munich weather demo returns `weather_observation: UNAVAILABLE` |
| RAG not ingested | CDQ showcase returns `rag_knowledge: INSUFFICIENT` |

REST Countries check during capture:

```bash
curl -sL 'https://restcountries.com/v3.1/name/Germany?fields=name,capital'
# {"success": false, "errors": [{"message": "... migrate to ... v5 ..."}]}
```

## Runtime fixes applied for local stack

- MCP subprocess paths corrected for `spring-boot:run` JVM cwd (`../mvnw` → jar launch for countries).
- MCP client `initializationTimeout` aligned with configured timeout (60s).
- `ChatController` registered via `@Import` + `@Bean` (`ChatWebConfiguration`) to fix `@ConditionalOnBean` ordering with component scan.

## Trace capture

Structured request traces logged by `AssistantRequestTrace` (correlation id, route, ports, RAG count, outcome). Excerpts copied to `docs/demo/request-traces/`.
