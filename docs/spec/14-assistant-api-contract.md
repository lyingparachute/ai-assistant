# Assistant API Contract

HTTP contract for the Chat Interface (`chat-ui/`) and E2E tests. The assistant application exposes JSON only; it does not serve HTML.

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

## Success response

`200 OK`, `Content-Type: application/json`

| Field | Type | Required | Rules |
| --- | --- | --- | --- |
| `answerText` | string | yes | Non-blank assistant answer text |
| `sources` | array | yes | Zero or more discriminated source entries (see below) |
| `traceCorrelationId` | string | no | Present when orchestration assigned a trace id; omitted when absent |

Source-unavailable orchestration outcomes use `200` with structured `sources[]` and honest `answerText`. They are not HTTP errors.

### Example (country facts)

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

## Client error response

`400 Bad Request`, `Content-Type: application/json`

Returned for blank or missing `question`, malformed JSON, or bean-validation failures.

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

## Server error response

`5xx`, `Content-Type: application/json`

Unexpected failures only. No stack traces or internal paths in the body.

| Field | Type |
| --- | --- |
| `error` | string (e.g. `internal_error`) |
| `message` | string (generic safe message) |

## CORS

Browser clients (Astro dev server) call the API cross-origin. The backend enables CORS for configured origins.

| Property | Environment variable | Default | Description |
| --- | --- | --- | --- |
| `assistant.cors.allowed-origins` | `ASSISTANT_CORS_ALLOWED_ORIGINS` (comma-separated) | `http://localhost:4321` | Origins allowed for `POST /api/chat` and preflight |

Preflight (`OPTIONS`) must succeed for allowed origins.

## Controller boundary

`ChatController` maps `ChatRequest` → `UserQuestion`, calls `AnswerQuestionUseCase.answer`, maps `ConversationTurn` → `ChatResponse`. No port injection, routing, or orchestration in the controller.

## Mapping notes

- `UserQuestion` trims and rejects blank text at construction; invalid input surfaces as `400`.
- `traceCorrelationId` is omitted from JSON when `AssistantAnswer` has no trace.
- Weather `timestamp.kind` is `observed` for `WeatherTimestamp.Observed` and `retrieved` for `WeatherTimestamp.Retrieved`. A retrieval time is never labeled as observed.
