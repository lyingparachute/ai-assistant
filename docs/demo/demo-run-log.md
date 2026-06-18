# Demo Run Log

## Capture session 3: **2026-06-18** (M6 streaming docs) — live stack unavailable

Streaming plan M6 documentation and boundary verification. The assistant was not running on
`http://localhost:8080` during this session (`curl` health probe failed), so demo re-capture and
`./mvnw verify -P e2e` were **skipped** — not substituted with fabricated output.

### SSE contract samples (boundary tests, not live)

Deterministic route (`germany-capital`) — `trace` + `final`, no `token` (from
`e2e-tests/src/test/resources/fixtures/germany-capital-sse-stream.txt`, matches
`ChatControllerStreamingTest`):

```text
event:trace
data:{"type":"countries_facts","status":"USED"}

event:final
data:{"answerText":"The capital of Germany is Berlin.","sources":[{"type":"countries_facts","status":"USED","countryInfo":{"countryName":"Germany","capital":"Berlin","region":"Europe","population":83497147}}],"traceCorrelationId":"d4eb3d1b-2049-42a9-91c6-ba26dfcdfd38"}
```

Synthesis route shape — `trace`, `token`(s), `model_synthesis` `trace`, `final` (representative
from `chat-ui/src/lib/sseParser.test.ts`; live token text varies):

```text
event:trace
data:{"type":"countries_facts","status":"USED"}

event:token
data:{"text":"Berlin "}

event:token
data:{"text":"is great."}

event:final
data:{"answerText":"Berlin is great.","sources":[{"type":"countries_facts","status":"USED"}],"traceCorrelationId":"trace-1"}
```

### Verification run (hermetic)

```bash
./mvnw test
cd chat-ui && npm run build
```

```text
./mvnw test → BUILD SUCCESS
  assistant-app: Tests run: 237, Failures: 0, Errors: 0, Skipped: 0
  countries-mcp-server: Tests run: 33, Failures: 0, Errors: 0, Skipped: 0
  e2e-tests: Tests run: 3, Failures: 0, Errors: 0, Skipped: 0

cd chat-ui && npm run build → Complete! (1 page built)
```

### Skipped (live stack down)

| Step | Reason |
| --- | --- |
| `./scripts/capture-demo-answers.sh` | No assistant on port 8080 |
| `./mvnw verify -P e2e` | Requires live stack |

Prior capture session 2 evidence (`2026-06-17`) remains the latest live demo capture until a new
session succeeds.

### CONTEXT glossary check

`CONTEXT.md` already defines **Streamed Answer** and **Source-Usage Trace** with correct Avoid
lists; no glossary changes required for M6.

---

## Capture session 2: **2026-06-17** (local machine) — fully grounded

All three 2026-06-16 blockers resolved; every required question now answered from a live
source. Curated answers: `final-answers.md`. Per-question traces: `request-traces/`. Raw
JSON: `capture/`.

| Prior blocker | Resolution |
| --- | --- |
| REST Countries v3.1 deprecated | Migrated countries-mcp-server to REST Countries **v5** (ADR `0008`, commit `57fbd9b`). `country_lookup` returns Germany→Berlin, region Europe, population 83,497,147. |
| `WEATHER_API_KEY` not configured | Key configured via gitignored `.env` (sourced by `start-assistant.sh`). Munich 23.2°C, Berlin 19.1°C, real retrieval times. |
| RAG not ingested | `rag_chunks` populated from the CDQ Fraud Guard page; CDQ showcase returns `rag_knowledge: USED` with 5 snippets (top similarity 0.87). |

Two latent runtime bugs were also found and fixed during live verification (both invisible to
unit tests because `StdioMcpToolInvoker` is `@Profile("!test")`): a Spring two-constructor
startup failure, and a null-`isError()` NPE that turned successful MCP calls into failures.
See `implementation-notes.md`.

E2E demo verification against the live stack: `./mvnw -pl e2e-tests verify -P e2e` →
`RequiredDemoQuestionsIT` Tests run: 5, Failures: 0, Errors: 0, BUILD SUCCESS.

---

## Capture session 1: **2026-06-16** (local machine) — blocked (historical)

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
