# Assistant API Contract

HTTP contract for the Chat Interface (`chat-ui/`) and E2E tests. The assistant application exposes JSON over Server-Sent Events (SSE); it does not serve HTML.

## Endpoint

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/api/chat` | Answer one user question (single-turn; no history fields) |

## Request

`Content-Type: application/json`

| Field | Type | Required | Rules |
| --- | --- | --- | --- |
| `question` | string | yes | Non-blank after trim |

### Example

```json
{
  "question": "What is the capital city of Germany?"
}
```

## Success response (open stream)

`200 OK`, `Content-Type: text/event-stream`

The response is a Server-Sent Events stream. Each event has a named `event:` line and a JSON `data:` payload. The stream delivers a **Streamed Answer** and a live **Source-Usage Trace**; the authoritative answer is always the terminal `final` event. Earlier `token` events are progressive and never stand as the final answer.

### Event types

| Event | When emitted | Payload shape |
| --- | --- | --- |
| `trace` | A Knowledge Source outcome is determined | `{ "type": string, "status": string }` |
| `token` | A non-blank LLM delta arrives (synthesis routes only) | `{ "text": string }` |
| `final` | Orchestration completes successfully (exactly one terminal event) | `ChatResponse` (see below) |
| `error` | Unexpected failure after the stream has opened (exactly one terminal event) | `{ "error": string, "message": string }` |

`trace.type` matches the `sources[].type` discriminator (`countries_facts`, `weather_observation`, `rag_knowledge`, `model_synthesis`). `trace.status` is one of `USED`, `UNAVAILABLE`, or `INSUFFICIENT`.

Deterministic routes (country-only, weather-only, combined country-weather, unsupported) emit `trace` and `final` only — no `token` events. Synthesis routes (`PlaceSynthesis`, `CdqProduct`) may emit `token` events before `final`. On synthesis routes, `model_synthesis` `trace` is emitted only after `LlmPort.generate` returns, so tokens may appear before the synthesis trace.

Source-unavailable orchestration outcomes still end with a `final` event carrying honest `sources[]` and `answerText`. They are not HTTP errors and do not use the `error` event.

### Example stream (deterministic route — no tokens)

Question: `What is the capital city of Germany?`

```text
event:trace
data:{"type":"countries_facts","status":"USED"}

event:final
data:{"answerText":"The capital of Germany is Berlin.","sources":[{"type":"countries_facts","status":"USED","countryInfo":{"countryName":"Germany","capital":"Berlin","region":"Europe","population":83497147}}],"traceCorrelationId":"d4eb3d1b-2049-42a9-91c6-ba26dfcdfd38"}
```

### Example stream (synthesis route — tokens + final)

Question: `What do you know about Berlin?` (representative shape; token text varies per run)

```text
event:trace
data:{"type":"countries_facts","status":"USED"}

event:token
data:{"text":"Berlin is the capital of Germany. "}

event:token
data:{"text":"It is a major city in Europe."}

event:trace
data:{"type":"model_synthesis","status":"USED"}

event:final
data:{"answerText":"Berlin is the capital of Germany. It is a major city in Europe.","sources":[{"type":"countries_facts","status":"USED","countryInfo":{...}},{"type":"model_synthesis","status":"USED"}],"traceCorrelationId":"..."}
```

### `final` payload (`ChatResponse`)

| Field | Type | Required | Rules |
| --- | --- | --- | --- |
| `answerText` | string | yes | Non-blank assistant answer text |
| `sources` | array | yes | Zero or more discriminated source entries (see below) |
| `traceCorrelationId` | string | no | Present when orchestration assigned a trace id; omitted when absent |

### Example `final` (country facts)

```json
{
  "answerText": "The capital of Germany is Berlin.",
  "sources": [
    {
      "type": "countries_facts",
      "status": "USED",
      "countryInfo": {
        "countryName": "Germany",
        "capital": "Berlin",
        "region": "Europe",
        "population": 83240525
      }
    }
  ],
  "traceCorrelationId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

## Source entry shapes

Each element of `sources[]` is a discriminated object. The `type` field selects the variant. The `status` field is one of `USED`, `UNAVAILABLE`, or `INSUFFICIENT` (RAG only for `INSUFFICIENT`).

### `countries_facts`

| Field | When present | Type |
| --- | --- | --- |
| `type` | always | `"countries_facts"` |
| `status` | always | `USED` or `UNAVAILABLE` |
| `countryInfo` | `status` = `USED` | object: `countryName`, `capital`, `region`, `population` |
| `unavailableMessage` | `status` = `UNAVAILABLE` | string |
| `unavailableHint` | `status` = `UNAVAILABLE` | string |

### `weather_observation`

| Field | When present | Type |
| --- | --- | --- |
| `type` | always | `"weather_observation"` |
| `status` | always | `USED` or `UNAVAILABLE` |
| `weatherReport` | `status` = `USED` | object (see below) |
| `unavailableMessage` | `status` = `UNAVAILABLE` | string |
| `unavailableHint` | `status` = `UNAVAILABLE` | string |

`weatherReport`:

| Field | Type |
| --- | --- |
| `location.city` | string |
| `temperature.celsius` | number |
| `timestamp.kind` | `"observed"` or `"retrieved"` |
| `timestamp.value` | ISO-8601 instant string |

### `rag_knowledge`

| Field | When present | Type |
| --- | --- | --- |
| `type` | always | `"rag_knowledge"` |
| `status` | always | `USED`, `INSUFFICIENT`, or `UNAVAILABLE` |
| `snippets` | `status` = `USED` | array of snippet objects |
| `unavailableMessage` | `status` = `UNAVAILABLE` | string |
| `unavailableHint` | `status` = `UNAVAILABLE` | string |

Snippet object: `chunkText`, `sourceUrl`, `contentHash`, `chunkIndex`, and optional `retrievalSimilarityScore` (0.0–1.0).

### `model_synthesis`

| Field | When present | Type |
| --- | --- | --- |
| `type` | always | `"model_synthesis"` |
| `status` | always | `USED` or `UNAVAILABLE` |
| `unavailableMessage` | `status` = `UNAVAILABLE` | string |
| `unavailableHint` | `status` = `UNAVAILABLE` | string |

`USED` model synthesis marks contribution only; synthesis text lives in `answerText`.

## Client error response (pre-stream)

`400 Bad Request`, `Content-Type: application/json`

Returned for blank or missing `question`, malformed JSON, or bean-validation failures **before** the SSE stream opens.

| Field | Type |
| --- | --- |
| `error` | string (short code, e.g. `validation_failed`) |
| `message` | string (human-readable detail) |

### Example

```json
{
  "error": "validation_failed",
  "message": "question must not be blank"
}
```

## Post-stream error event

When an unexpected failure occurs after the stream has opened, the HTTP status remains `200` and the stream ends with a single `error` event (not HTTP `5xx`). No stack traces or internal paths in the payload.

| Field | Type |
| --- | --- |
| `error` | string (e.g. `internal_error`) |
| `message` | string (generic safe message) |

### Example

```text
event:error
data:{"error":"internal_error","message":"an unexpected error occurred"}
```

## Manual curl probe

Use `curl -N` (no buffer) so events appear as they arrive:

```bash
curl -sfN -X POST http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"question":"What is the capital city of Germany?"}'
```

## CORS

Browser clients (Astro dev server) call the API cross-origin. The backend enables CORS for configured origins.

| Property | Environment variable | Default | Description |
| --- | --- | --- | --- |
| `assistant.cors.allowed-origins` | `ASSISTANT_CORS_ALLOWED_ORIGINS` (comma-separated) | `http://localhost:4321` | Origins allowed for `POST /api/chat` and preflight |

Preflight (`OPTIONS`) must succeed for allowed origins. Browser clients must not send a custom `Accept` header; rely on the default fetch accept plus `Content-Type: application/json`.

## Controller boundary

`ChatController` maps `ChatRequest` → `UserQuestion`, constructs a per-request `AssistantResponseSink`, runs `AnswerQuestionUseCase.answer(question, sink)` on a bounded executor, and maps `ConversationTurn` → SSE events via `SseAssistantResponseSink`. No port injection, routing, or orchestration in the controller.

## Mapping notes

- `UserQuestion` trims and rejects blank text at construction; invalid input surfaces as `400` JSON before streaming starts.
- `traceCorrelationId` is omitted from JSON when `AssistantAnswer` has no trace.
- Weather `timestamp.kind` is `observed` for `WeatherTimestamp.Observed` and `retrieved` for `WeatherTimestamp.Retrieved`. A retrieval time is never labeled as observed.
- The `final` event JSON is semantically equal to the prior blocking JSON contract; consumers extract it as the authoritative answer.
