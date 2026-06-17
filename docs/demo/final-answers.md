# Final Demo Answers

Captured from the running assistant on **2026-06-16T16:21:16Z** (UTC).

## Local configuration summary

| Setting | Value |
| --- | --- |
| Assistant API | `http://localhost:8080` |
| Chat Interface | `http://localhost:4321` |
| Ollama chat model | `qwen3:4b` |
| Ollama embedding model | `nomic-embed-text` |
| pgvector | Docker `pgvector/pgvector:pg17`, database `assistant_rag` |
| Countries MCP | `java -jar countries-mcp-server-0.1.0-SNAPSHOT.jar` (stdio) |
| Weather MCP | `scripts/mcp-weather` → semdin/mcp-weather |
| REST Countries base URL | `https://restcountries.com/v3.1` (deprecated upstream) |
| `WEATHER_API_KEY` | not configured in capture environment |
| RAG ingestion | not completed (`rag_chunks` count 0 at capture time) |

## Required questions

### 1. What is the capital city of Germany?

**Answer (from running assistant):**

> Countries MCP is unavailable: REST Countries source unavailable

**Source path:** Chat Interface → Assistant API → `AnswerQuestionUseCase` → `CountriesPort` → countries MCP → REST Countries (v3.1 deprecated)

**Trace:** `d48e4298-eff0-45df-bc3b-80b62d815c54` — see [request-traces/01-germany-capital.txt](request-traces/01-germany-capital.txt)

**Note:** Live REST Countries v3.1 returns a deprecation payload (`success: false`). The assistant correctly reports source-unavailable instead of inventing Berlin.

---

### 2. What is the temperature currently in Munich?

**Answer (from running assistant):**

> Weather MCP is unavailable: MCP tool call failed for get-weather

**Source path:** Chat Interface → Assistant API → `AnswerQuestionUseCase` → `WeatherPort` → weather MCP

**Trace:** `2758fef9-2b87-4144-8958-2f7b1e615c1e` — see [request-traces/02-munich-weather.txt](request-traces/02-munich-weather.txt)

**Note:** `WEATHER_API_KEY` was not set during capture. No temperature was invented.

---

### 3. What is the temperature of the capital of Germany currently?

**Answer (from running assistant):**

> Countries MCP is unavailable: REST Countries source unavailable

**Source path:** Chat Interface → Assistant API → `AnswerQuestionUseCase` → `CountriesPort` (capital resolution) → would continue to `WeatherPort` when countries succeeds

**Trace:** `7b30e13e-5ad7-49f0-b357-05c3b34e5269` — see [request-traces/03-germany-capital-weather.txt](request-traces/03-germany-capital-weather.txt)

---

### 4. What do you know about Berlin?

**Answer (from running assistant):**

> Countries MCP is unavailable: REST Countries source unavailable

**Source path:** Chat Interface → Assistant API → `AnswerQuestionUseCase` → `CountriesPort` → (intended) `LlmPort` synthesis after country facts

**Trace:** `04e52a3e-a117-45cd-9ee8-117fef8b30c3` — see [request-traces/04-berlin-place.txt](request-traces/04-berlin-place.txt)

**Note:** Weather and RAG ports did not fire (`ragRetrievalCount=0` in trace).

---

## Showcase question (CDQ Fraud Guard)

**Question:** What is CDQ Fraud Guard?

**Answer (from running assistant):**

> I have insufficient product knowledge to answer this CDQ Fraud Guard question.

**Source path:** Chat Interface → Assistant API → `AnswerQuestionUseCase` → `RagKnowledgePort` (0 snippets) → insufficient-knowledge response

**Trace:** `c2ac2fe9-561b-42d8-91a8-5daef28519cd` — see [request-traces/05-cdq-showcase.txt](request-traces/05-cdq-showcase.txt)

---

## Source-unavailable scenario

**Question:** What is the capital city of Atlantis?

**Answer (from running assistant):**

> I cannot answer this question: No matching source route for this question

**Trace:** `743d56e0-4bcf-4972-a0df-3698914c7ac2` — see [request-traces/06-unsupported-route.txt](request-traces/06-unsupported-route.txt)

---

## Capture artifacts

Raw JSON responses: `docs/demo/capture/*.json`
