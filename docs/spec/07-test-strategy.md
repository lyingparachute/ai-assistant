# Test Strategy

The test strategy favors boundary and integration-style tests over brittle tests that mock every internal call. Tests should prove source routing, source-unavailable behavior, RAG retrieval, MCP behavior, and required demo-question paths.

## Test Levels

### Unit Tests

Use unit tests for deterministic domain behavior and small policies:

- `UserQuestion` validation and normalization.
- `AssistantAnswer` source metadata behavior.
- `ResponseComposer` formatting rules.
- request-routing policy for required question types.
- RAG chunking and metadata generation.
- source-unavailable outcome mapping.

Unit tests must not call Ollama, REST Countries, weather services, or pgvector.

### Contract Tests

Use contract tests for module boundaries and external protocol shapes:

- Assistant API request and response payloads.
- Countries MCP tool input and output schema.
- Weather MCP adapter expected input and output mapping.
- REST Countries adapter response mapping from representative JSON fixtures.
- RAG repository read/write shape, including required metadata.
- Local MCP configuration shape for command, args, environment variables, working directory, and timeout.

Contract tests should fail when a public boundary changes without documentation and test updates.

MCP schema contract tests should verify that tool names, descriptions, required fields, and error shapes are assistant-facing and stable. They should avoid exposing upstream-only fields that the model does not need.

### Integration Tests

Use integration tests for important behavior across adapters:

- Assistant orchestration with controlled LLM, RAG, countries, and weather adapters.
- Countries MCP server against a stubbed REST Countries HTTP server.
- pgvector adapter against a real PostgreSQL pgvector container.
- HTTP chat endpoint with controlled application ports.

Prefer controlled local dependencies over live network calls in automated tests.

## Testcontainers

Use Testcontainers where it provides stable local proof:

- PostgreSQL with `pgvector/pgvector:pg17` for vector schema, storage, and retrieval tests.
- Optional stub HTTP containers only if simpler in the selected test stack; otherwise use in-process stub servers.

Testcontainers tests should verify:

- pgvector extension and schema initialization.
- chunk insertion with embedding values.
- similarity retrieval returns the expected chunk for representative input.
- clean database setup produces deterministic retrievable data.

## MCP Tool Tests

### Countries MCP Server

Required tests:

- Germany lookup returns Berlin through the MCP tool path.
- Berlin lookup resolves to Germany through the capital-city path of the same tool.
- Invalid country returns a typed not-found or source-unavailable result with a recovery hint.
- REST Countries timeout or error is surfaced as a source failure.
- Tool handler wraps expected failures into structured MCP tool errors without crashing the server.
- Startup configuration is validated before tool registration.
- Graceful shutdown does not leave the local MCP process hanging.
- Tool output does not expose raw upstream JSON unless intentionally part of the contract.

### Weather MCP Integration

Required tests:

- Munich weather lookup maps a controlled MCP response into `WeatherReport`.
- Weather result includes location and a `WeatherTimestamp` labeled as observed time or retrieval time.
- Weather MCP startup or tool-call failure maps to source-unavailable behavior.
- No test invents a temperature when the source is unavailable.

## RAG Retrieval Tests

### Ingestion Tests

Required tests:

- representative CDQ Fraud Guard product text is extracted or accepted as plain text in a controlled fixture;
- chunking produces deterministic chunk indexes and source metadata;
- embeddings are stored with chunk text, source URL, content hash, and ingestion timestamp;
- optional ingestion artifacts describe extracted text, chunk count, and skipped or changed content;
- re-running ingestion for the same content is idempotent: it replaces the prior chunk set for the source rather than appending duplicates.

### Retrieval Tests

Required tests:

- a CDQ Fraud Guard question retrieves relevant `KnowledgeSnippet` values from pgvector;
- a question outside the product content returns no relevant snippets;
- retrieval applies configured top-k and relevance threshold;
- retrieved snippets preserve source URL and chunk metadata for answer grounding;
- retrieved prompt context stays below the configured snippet and token budget.

## Assistant Orchestration Tests

Use controlled port implementations to verify behavior without calling real external services.

Required tests:

- "What is the capital city of Germany?" calls countries capability and returns Berlin from `CountryInfo`.
- "What is the temperature currently in Munich?" calls weather capability and includes location and timestamp.
- "What is the temperature of the capital of Germany currently?" calls countries first and weather second.
- "What do you know about Berlin?" fires the countries and LLM ports only, resolves Berlin to Germany through the countries capability, and produces a concise answer that states Berlin is the capital of Germany without firing weather or RAG and without mislabeling model synthesis as a tool result.
- CDQ Fraud Guard product question retrieves RAG knowledge and sends grounded context to the LLM port.
- RAG no-result path reports insufficient product knowledge.
- Ollama unavailable path returns a clear failure when model synthesis is required.
- countries or weather unavailable paths do not fall back to fabricated model facts.
- LLM port receives compact grounded context rather than raw page dumps or raw tool payloads.
- Any tool loop, if introduced, stops at configured max turns and respects timeout or cancellation signals.

## Evaluation Dataset

Maintain a small repeatable dataset for routing and answer-grounding behavior. It can be implemented as Java tests, E2E fixtures, or an offline evaluation tool later.

Minimum dataset categories:

- country-only questions;
- weather-only questions;
- country plus weather questions;
- CDQ Fraud Guard product questions;
- no-result product questions;
- unavailable-source scenarios;
- broad synthesis questions where model output must not be mislabeled as tool output.

The dataset should prefer deterministic assertions for source routing, required fields, and source-unavailable behavior. Natural-language answer quality can be reviewed manually or with optional offline evaluation, but manual vibe checks alone are not enough.

## E2E Tests for Required Demo Questions

E2E tests or scripts should run against the locally started assistant and configured dependencies.

Required questions:

- "What is the capital city of Germany?"
- "What is the temperature currently in Munich?"
- "What is the temperature of the capital of Germany currently?"
- "What do you know about Berlin?"

E2E verification should assert source path evidence, not exact volatile weather values. Weather checks should assert that the answer includes:

- requested location or resolved capital location;
- current temperature value returned by the weather source;
- a weather timestamp labeled as observed time or retrieval time;
- weather source status.

E2E scripts should also capture trace or log evidence for selected source path, tool calls, RAG retrieval count, and source-unavailable outcomes.

## Manual Verification Checklist

Before final submission:

- Start pgvector using the documented local command.
- Start Ollama and confirm `qwen3:4b` is available.
- Start the custom countries MCP server.
- Start or configure the local weather MCP server.
- Run RAG ingestion for the CDQ Fraud Guard source.
- Start the assistant application.
- Ask each required demo question through the Chat Interface.
- Save demo answers from the running assistant only.
- Confirm each demo answer records or clearly implies the source path used.
- Save trace or log evidence showing request routing and tool/RAG calls.
- Stop one required source and verify source-unavailable behavior.
- Run focused automated tests and record actual command output.

## Verification Discipline

Do not claim a feature is complete because a mock returned the expected value. Completion requires the narrowest meaningful automated test plus any required local-runtime verification for the affected boundary.

Skipped external verification is acceptable only when the reason is explicit, such as missing weather API configuration or unavailable local dependency. The assistant must document the skipped verification and must not replace it with invented final answers.
