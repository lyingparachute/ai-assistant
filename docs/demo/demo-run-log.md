# Demo Run Log

## Capture session 5: **2026-06-18** (post-streaming SSE re-capture)

Re-capture requested after streaming landed. Capture and E2E verification ran against the live
assistant already listening on the canonical ports. Country facts, weather observations, pgvector
RAG retrieval, and Ollama synthesis were live. The RAG re-ingestion entry point is blocked;
existing ingested CDQ chunks were verified and used by the live product answer.

### Prerequisites checked

| Check | Result |
| --- | --- |
| Docker | running, server `29.5.2` |
| Ollama | running; models `qwen3:4b`, `nomic-embed-text` |
| `.env` | present; `REST_COUNTRIES_API_KEY`, `WEATHER_API_KEY`, `WEATHER_API_URL` set (values not logged) |
| Port 5432 | pgvector container `assistant-pgvector` (Up, bound to 5432) |
| Ports 8080 / 4321 | occupied by existing live stack (`java` on 8080, `node` on 4321) |

### Startup command check

```bash
START_ASSISTANT_NO_PAUSE=1 ./scripts/start-assistant.sh
```

Result:

```text
ERROR: Port 8080 is already in use. Stop the other process or set ASSISTANT_BACKEND_PORT.
```

The already-running stack was used for live E2E and demo capture. This launcher check does not
block acceptance because the live assistant responded on `http://localhost:8080`.

### RAG ingestion check

README command attempted:

```bash
./mvnw -pl assistant-app spring-boot:run -- --ingest-rag
```

Result:

```text
BUILD FAILURE
Unknown lifecycle phase "--ingest-rag"
```

Existing documented entry point attempted:

```bash
set -a; source .env; set +a
ASSISTANT_INGEST_RAG=true ./mvnw -pl assistant-app spring-boot:run
```

Result:

```text
APPLICATION FAILED TO START
Parameter 0 of constructor in ChatController required a bean of type
AnswerQuestionUseCase that could not be found.
```

No production code was changed. Existing RAG content was verified directly:

```bash
docker exec assistant-pgvector psql -U assistant -d assistant_rag -tAc "select count(*) from rag_chunks;"
```

```text
9
```

```bash
docker exec assistant-pgvector psql -U assistant -d assistant_rag -tAc \
  "select source_url, count(*), min(ingested_at), max(ingested_at) from rag_chunks group by source_url;"
```

```text
https://www.cdq.com/products/cdq-fraud-guard|9|2026-06-16 17:22:04.004052+00|2026-06-16 17:22:04.004052+00
```

The live CDQ demo answer retrieved 5 snippets from these chunks.

### Demo capture command

```bash
./scripts/capture-demo-answers.sh
```

```text
Capturing demo answers from http://localhost:8080 at 2026-06-18T08:02:27Z
exit 0; six questions captured from terminal SSE final events
```

Fresh raw files:

- `docs/demo/capture/01-germany-capital.json`
- `docs/demo/capture/02-munich-weather.json`
- `docs/demo/capture/03-germany-capital-weather.json`
- `docs/demo/capture/04-berlin-place.json`
- `docs/demo/capture/05-cdq-product.json`
- `docs/demo/capture/06-source-unavailable-invalid-country.json`

### SSE transport evidence

Deterministic route (`germany-capital`) emitted Source-Usage Trace + terminal `final`, no token
events:

```text
event:trace
data:{"type":"countries_facts","status":"USED"}

event:final
data:{"answerText":"The capital of Germany is Berlin.",...,"traceCorrelationId":"85172cb3-61f7-4866-b794-06afe66a3d17"}
```

Synthesis route (`berlin-place`) emitted Source-Usage Trace, token events, labelled synthesis, and
terminal `final`:

```text
event:trace
data:{"type":"countries_facts","status":"USED"}

event:token
data:{"text":"Berlin"}

event:final
data:{"answerText":"Berlin is the capital of Germany...",...,"traceCorrelationId":"ab2be3bb-a6ec-4c8f-88ff-c2a0146513ed"}
```

Trace excerpts with route, ports, RAG count, and outcomes were copied to
`docs/demo/request-traces/`.

Clean-checkout command table: [clean-checkout-verification.md](clean-checkout-verification.md).

Additional manual SSE probes were run after capture for synthesis evidence and saved locally under
`.local/logs/demo-sse/` (gitignored). The Berlin and CDQ probes showed `trace`, `token`,
`model_synthesis` trace, and terminal `final` events. A later Germany-only probe returned
`countries_facts: UNAVAILABLE` from the countries MCP path after the successful capture; it is not
used as happy-path evidence.

### E2E verification (live stack)

```bash
./mvnw verify -P e2e
```

```text
assistant-app: Tests run: 242, Failures: 0, Errors: 0, Skipped: 0
countries-mcp-server: Tests run: 33, Failures: 0, Errors: 0, Skipped: 0
e2e-tests unit: Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
RequiredDemoQuestionsIT: Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### Source availability

| Source | Status |
| --- | --- |
| REST Countries v5 (countries MCP) | live — Germany -> Berlin |
| WeatherAPI (weather MCP) | live — Munich 24.3°C, Berlin 23.4°C |
| pgvector RAG | live — 5 snippets retrieved for CDQ question |
| Ollama synthesis | live — place + CDQ answers produced through `model_synthesis: USED` |

### Blockers and notes

| Item | Status |
| --- | --- |
| README RAG command | blocked: Maven treats `--ingest-rag` as an unknown lifecycle phase |
| `ASSISTANT_INGEST_RAG=true` entry point | blocked: `ingest-rag` profile starts `ChatController` without `AnswerQuestionUseCase` |
| Source-unavailable demo | captured as unsupported deterministic route for Atlantis; no fabricated country fact |

## Chat Interface improvements verification — **2026-06-18**

Automated verification for `improve-chat-interface.md` M0–M5 (M2b deferred until agentic M5):

```bash
cd chat-ui && npm run test && npm run build
```

Result:

```text
Test Files  9 passed (9)
     Tests  42 passed (42)

astro build — 1 page(s) built — Complete!
```

Built `dist/index.html` includes `#message-thread` with `role="log"`, six `demo-chip` buttons
from `e2e-tests/.../demo-questions.json`, and sticky composer markup.

Manual checklist (§ Manual verification): not re-run in this session — live stack was not
available on canonical ports during M5 land. Prior capture session 5 (above) exercised the
Assistant API paths; Chat Interface session-display UX should be verified locally with
`./scripts/start-assistant.sh` and `cd chat-ui && npm run dev`.

M2b (`applyUnsupportedLayout` removal): **deferred** — gated on agentic orchestration M5 per plan.

## Prior captures

Older capture sessions remain in git history and previous file revisions. Session 5 above is the
current live demo evidence for post-streaming SSE.
