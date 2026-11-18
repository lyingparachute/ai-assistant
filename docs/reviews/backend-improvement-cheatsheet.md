# Backend Interview Cheat Sheet

> Purpose: quick interview preparation for explaining the Local AI Assistant backend: what is strong, what is weak, why the main decisions were made, and what could be improved next.

**One-line pitch:** A local-first Spring Boot Assistant API routes each question to verified Knowledge Sources, streams source trace and the final answer over SSE, and refuses to invent facts when a source is unavailable.

---

## 1. Mental Model

```text
Chat Interface
  -> POST /api/chat
  -> ChatController + SSE sink
  -> AnswerQuestionUseCase
  -> deterministic SourceRoutingPolicy
  -> ports: countries / weather / RAG / LLM
  -> ResponseComposer
  -> SSE final event
```

**Most important rule:** the model is not a verified fact source.

| Fact type | Required Knowledge Source | If unavailable |
| --- | --- | --- |
| Country facts | Countries MCP server -> REST Countries | Source-unavailable response |
| Weather observations | Weather MCP server -> WeatherAPI | Source-unavailable response |
| CDQ product knowledge | RAG knowledge in pgvector | Insufficient or unavailable response |
| Fluent wording | Ollama `qwen3:4b` | Synthesis unavailable response |

---

## 2. Tech Stack

| Technology | Used for | Interview explanation |
| --- | --- | --- |
| Java 21 | Backend language | Records and sealed interfaces make typed outcomes explicit. |
| Spring Boot 3.5.15 | HTTP, DI, config, validation | Keeps runtime wiring conventional while controllers stay thin. |
| Spring AI 1.1.8 | Ollama chat and embeddings adapters | Adapter helper only; not the core architecture. |
| Ollama `qwen3:4b` | Local synthesis model | Local-first, no cloud credentials; smaller model means routing stays deterministic. |
| Ollama `nomic-embed-text` | RAG embeddings | Produces 768-dimensional vectors for pgvector. |
| PostgreSQL pgvector | Vector database | Stores chunks, embeddings, source URL, content hash, index, timestamp. |
| MCP SDK 1.0.2 | stdio MCP client/server | Countries and weather integrations stay behind ports. |
| jsoup | Product page text extraction | Converts CDQ product page HTML into plain text for ingestion. |
| Testcontainers | pgvector integration tests | Real local proof for schema, storage, retrieval. |

**Good phrase:** "Spring AI and MCP SDK are infrastructure details. The application core depends on ports and typed results."

---

## 3. Decisions To Defend

| Decision | Why it was chosen | Trade-off |
| --- | --- | --- |
| Hexagonal architecture | Keeps orchestration testable and adapters replaceable. | More files/interfaces than a simple controller-first app. |
| Deterministic routing | Reproducible demo paths and no fabricated source use. | Not a general-purpose assistant yet. |
| SSE API | Live Source-Usage Trace and streamed answer tokens. | More transport complexity than single JSON. |
| MCP for countries/weather | Standard tool boundary and assignment fit. | Local subprocess lifecycle and config complexity. |
| Custom pgvector schema | Full control over chunk metadata and idempotent ingestion. | More custom SQL than generic VectorStore. |
| No conversation state | Avoids confusing chat history with Knowledge Sources. | Follow-up questions are out of scope. |
| Local Ollama | No cloud credentials and local reproducibility. | Quality/performance depend on reviewer hardware. |

---

## 4. Strengths To Emphasize

### Source Honesty

The assistant does not fabricate country facts, weather observations, or product knowledge. Failures become typed source-unavailable or insufficient-knowledge outcomes and visible source entries.

### Clean Boundaries

Domain/application code avoids Spring AI, MCP SDK, JDBC, HTTP clients, and SSE types. Those stay in adapters.

### Typed Outcomes

Expected states are modeled explicitly:

- `ToolExecutionResult`
- `RagRetrievalResult`
- `LlmResult`
- `SourceContributionStatus`

### Good Test Shape

The test strategy uses controlled ports for orchestration, contract tests for protocol shapes, Testcontainers for pgvector, and e2e parsing for SSE.

### Honest Documentation

README and demo docs name limitations, local dependencies, weather volatility, and source-unavailable behavior.

---

## 5. Weak Points To Admit

| Weak point | Honest explanation | Improvement direction |
| --- | --- | --- |
| Demo-scoped routing | Keyword routing is intentional for the assignment, but not general. | Intent classifier or bounded agentic fallback. |
| Agentic orchestration not shipped | ADR `0010` is proposed, not default behavior. | Add only with max turns, timeout, cancellation, evals. |
| Weather demo fragility | Weather depends on third-party local MCP and WeatherAPI config. | Readiness check and fake stdio contract tests. |
| RAG is MVP-level | Semantic-only retrieval with simple deterministic chunking. | Hybrid search, reranking, metadata, evals. |
| Client disconnect cancellation | Browser abort is handled, but downstream work may continue. | Propagate cancellation into MCP/Ollama/RAG calls. |
| Dependency-heavy setup | Docker, Ollama, keys, MCP scripts, ports must all work. | One readiness command and clearer diagnostics. |

**Good phrase:** "These are productization gaps, not hidden failures. The current design favors honest demo behavior over broad autonomy."

---

## 6. RAG: What It Is And How This Project Uses It

> RAG means: retrieve relevant external knowledge first, then ask the model to write an answer using that retrieved knowledge. The model is a writer/synthesizer, not the source of product facts.

### RAG In Theory

| Stage | What happens | Why it exists |
| --- | --- | --- |
| Ingestion | Source content is fetched, cleaned, chunked, embedded, and stored. | Prepares external knowledge for later search. |
| Retrieval | The user question is embedded and compared with stored chunk embeddings. | Finds only the chunks likely relevant to the question. |
| Augmentation | Retrieved snippets are inserted into the model prompt as grounded facts. | Gives the model the facts it may use. |
| Generation | The model writes the final answer from those grounded facts. | Produces readable language without inventing facts. |

### Current Ingestion Flow

```text
CDQ Fraud Guard page
  -> fetch HTML
  -> extract plain text with jsoup
  -> normalize whitespace
  -> compute content hash
  -> skip if unchanged
  -> split into deterministic chunks
  -> embed each chunk with `search_document:` prefix
  -> store in pgvector
```

Stored fields:

| Field | Meaning |
| --- | --- |
| `chunk_text` | Text fragment sent later as grounded fact. |
| `embedding vector(768)` | `nomic-embed-text` representation for similarity search. |
| `source_url` | Original CDQ Fraud Guard product page. |
| `content_hash` | Detects unchanged vs changed source content. |
| `chunk_index` | Stable order of chunks within the source. |
| `ingested_at` | When the chunk set was stored. |

Current chunking:

- max chunk size: `1000` characters;
- overlap: `200` characters;
- tries to end chunks on `.`, `!`, or `?`;
- deterministic indexes make tests and re-ingestion easier.

### Current Retrieval Flow

```text
User asks CDQ/Fraud Guard question
  -> route = CDQ_PRODUCT
  -> embed question with `search_query:` prefix
  -> pgvector cosine similarity search
  -> filter by relevance threshold
  -> keep top-k snippets
  -> if no snippets: insufficient product knowledge
  -> if snippets found: call Ollama with grounded facts
```

Current defaults:

| Setting | Value |
| --- | --- |
| Embedding model | `nomic-embed-text` |
| Vector size | `768` |
| Similarity method | cosine similarity via pgvector |
| `top-k` | `5` |
| relevance threshold | `0.5` |

### What The Model Receives

The model does not receive RAG snippets as JSON. The backend converts `KnowledgeSnippet` values into a plain-text prompt.

The prompt shape is:

```text
Answer using only the grounded product knowledge snippets. Do not invent product features not supported by the snippets.
Grounded facts:
- <retrieved chunk text 1>
- <retrieved chunk text 2>
- <retrieved chunk text 3>
User question: <original user question>
```

Important detail: the model currently receives only `chunkText`. Metadata such as `sourceUrl`, `contentHash`, `chunkIndex`, and similarity score stays in the backend and is returned later in the API `sources[]` payload.

### What The Model Returns

Ollama streams text deltas. The backend:

1. sends each non-blank delta to the client as an SSE `token`;
2. appends deltas into a final string;
3. returns `LlmResult.Success(text)` if the final text is non-blank;
4. returns `LlmResult.SourceUnavailable` if the model fails, times out, or returns blank text.

The final API answer is not just the model text. `ResponseComposer` combines:

- `answerText` from model synthesis;
- `rag_knowledge` source with retrieved snippets and metadata;
- `model_synthesis` source showing that Ollama contributed wording.

### Current RAG Strengths

| Strength | Why it is good |
| --- | --- |
| No snippets, no answer | Product questions do not fall back to model guesses. |
| Idempotent ingestion | Same source content does not create duplicate chunks. |
| Explicit schema | Reviewers can inspect exactly what is stored. |
| Testcontainers coverage | pgvector behavior is tested against a real database. |
| Source metadata preserved | The API can show where snippets came from. |

### Current RAG Limits

| Limit | Why it matters |
| --- | --- |
| Semantic search only | May miss exact terms, acronyms, or product names. |
| Simple character chunking | May cut across semantic section boundaries. |
| No reranking | Top vector matches go straight to the model. |
| No section metadata | Retrieval cannot filter by page section or heading. |
| No mature eval dataset | Hard to prove that tuning improved retrieval. |
| Metadata not in prompt | The model cannot cite chunk indexes or source URLs directly. |

### RAG Improvement Ideas

| Priority | Topic | What it is | Why it matters |
| --- | --- | --- | --- |
| P0 | RAG evals | Fixed dataset of product questions, expected chunks, and refusal cases. | Gives proof that changes improve retrieval. |
| P1 | Hybrid search | Combine pgvector semantic search with PostgreSQL full-text search. | Semantic search handles meaning; FTS handles exact names, acronyms, phrases. |
| P1 | Reranking | Retrieve more candidates, then choose the best few. | Improves precision before sending snippets to the model. |
| P1 | Structure-aware chunking | Split by headings, sections, paragraphs, sentences, then characters. | Keeps product claims attached to section meaning. |
| P1 | Metadata filtering | Store section title, heading path, language, product name, source version. | Filters before ranking instead of relying only on similarity. |
| P2 | Query rewrite | Expand short product questions into richer search queries. | Helps vague questions retrieve relevant snippets. |
| P2 | Query decomposition | Split multi-part product questions into multiple retrieval tasks. | Improves coverage for complex questions. |
| P2 | Contextual RAG | Add short chunk-level context summaries during ingestion. | Helps chunks with pronouns or references like "this capability." |
| P2 | Prompt injection hardening | Treat extracted page text as untrusted source material. | Prevents retrieved content from overriding system instructions. |
| P2 | Citation-aware prompting | Include snippet IDs/source URLs in prompt and ask model to cite them. | Makes final answers easier to verify. |
| P3 | Embedding lifecycle automation | Re-ingest when model, prefixes, dimension, or chunking change. | `vector(768)` ties schema to `nomic-embed-text`. |

**Current state:** deterministic, honest, semantic-only retrieval over CDQ Fraud Guard product-page content.

**Best first improvement:** RAG evals, then hybrid search with RRF, then reranking.

**Interview phrase:** "My RAG is intentionally conservative: if retrieval cannot find relevant snippets, the assistant reports insufficient product knowledge instead of asking the model to guess."

---

## 7. MCP Improvements

| Topic | What it is | Why it matters |
| --- | --- | --- |
| More precise naming | Consider namespaced names such as `countries__country_lookup`. | Reduces collisions in larger MCP hosts. |
| Description enrichment | State what `country_lookup` returns. | Helps models decide whether the tool is sufficient. |
| Better outcome types | Preserve invalid input, ambiguous, not recognized, and unavailable separately. | Improves user-facing honesty and recovery hints. |
| Tool-use evals | Dataset checking tool choice and argument filling. | Needed before model-driven tool selection. |
| Tool-call observability | Log sanitized input, output status, duration, error class. | Easier demo/debug when subprocesses fail. |
| Bounded agentic tool use | Max turns, deadlines, cancellation, typed `{ ok, error, hint }`. | Prevents loops and fabricated answers. |
| MCP lifecycle recovery | Handle startup, subprocess crash, timeouts, graceful shutdown. | stdio tools are local processes, not just method calls. |
| Tool argument validation | Reject multiple cities, blank values, unsupported formats, and ambiguous inputs before calling providers. | Prevents a model-driven tool loop from sending bad arguments such as `Munich and Berlin`. |
| Did-you-mean recovery hints | Return deterministic suggestions for likely typos when available. | Helps the model recover without guessing or fabricating source results. |

**Current countries MCP strength:** it exposes a semantic `country_lookup` tool instead of raw REST Countries JSON.

**Weather caveat:** weather uses an external local MCP server, so transport and provider reliability are weaker than the custom countries server.

---

## 8. Routing And Orchestration Improvements

| Topic | What it is | Why it matters |
| --- | --- | --- |
| Intent classifier | Classify country, weather, RAG, place synthesis, unsupported. | Replaces brittle keyword checks while preserving demo routes. |
| Agentic fallback mode | Use tools for unsupported questions only. | Adds flexibility without destabilizing demo paths. |
| Opt-in agentic architecture | ADR `0010`: mode switch, facade, `LlmToolCallPort`, max turns. | Clear path to agentic behavior without mixing synthesis and tool loops. |
| Better partial answers | Generalize partial success when one source works and another fails. | Makes multi-source answers more useful. |
| Stronger source outcomes | Separate unavailable, invalid, ambiguous, no result, insufficient, unexpected. | More precise behavior and tests. |
| One tool call per entity | If a user asks about two cities, the orchestrator should call weather once per city. | Keeps tool schemas simple and makes source traces precise. |

**Default stance:** deterministic mode should remain default until evals prove the agentic mode is reliable.

---

## 9. Streaming API Improvements

| Topic | What it is | Why it matters |
| --- | --- | --- |
| Backend cancellation | Propagate client abort to MCP, pgvector, and Ollama work. | Avoids wasted local work after Stop/disconnect. |
| Richer trace events | Include safe labels, elapsed time, stage names. | Better live debugging and demo clarity. |
| Error taxonomy | Stable codes for timeout, dependency outage, malformed payload, internal failure. | Helps frontend and tests respond precisely. |
| Contract compatibility | Preserve exactly one terminal `final` or `error` event. | Avoids breaking UI/e2e/capture clients. |
| CORS coverage | Keep Astro origin preflight tested. | Browser clients depend on cross-origin POST. |

**SSE contract line:** `final` is authoritative; token events are provisional.

---

## 10. LLM And Prompting Improvements

| Topic | What it is | Why it matters |
| --- | --- | --- |
| Prompt evals | Fixtures for place synthesis and CDQ product synthesis. | Verifies grounded-only answers. |
| Structured synthesis output | Ask for claims plus answer text, then validate claims. | Stronger guard against unsupported claims. |
| Model fallback policy | Define behavior for slow/unavailable/blank local model. | Keeps source honesty while improving reliability. |
| Prompt injection guardrails | Keep retrieved snippets clearly labeled as source material. | Reduces risk from untrusted page content. |

**Important distinction:** model synthesis can improve wording, but it must not upgrade unverified content into a verified fact.

---

## 11. Data And Configuration Improvements

| Topic | What it is | Why it matters |
| --- | --- | --- |
| Tuning profiles | Conservative/balanced/recall-heavy RAG settings. | Makes retrieval experiments repeatable. |
| Startup readiness check | Verify Ollama, models, pgvector, MCP jar, API keys, ports. | Prevents demo surprises. |
| Secret-safe diagnostics | Say which config is missing without printing secret values. | Useful and safe troubleshooting. |
| Profile/bean matrix | Document normal, `test`, and `ingest-rag` bean topology. | Explains why some beans are absent in ingestion mode. |

---

## 12. Countries MCP Server Improvements

| Topic | What it is | Why it matters |
| --- | --- | --- |
| Expressive outcomes | Preserve not-recognized and ambiguous-capital to final answer. | Avoids flattening user/input issues into source outage. |
| Did-you-mean hints | Suggest likely country/capital when deterministic support exists. | Better recovery without model guesses. |
| Capability versioning | Version/log schema changes. | Tool schema changes can break trusted hosts and clients. |

---

## 13. Weather Integration Improvements

| Topic | What it is | Why it matters |
| --- | --- | --- |
| Fake stdio MCP contract tests | Black-box tests against controlled weather subprocess. | Proves transport behavior, not only mappers. |
| Timestamp provenance tests | Keep observed vs retrieved timestamps distinct. | Weather values are volatile; provenance matters. |
| Provider error classification | Missing key, provider rejection, timeout, malformed payload, no observation. | Better hints and clearer demo failures. |
| Multi-location guard | Return a typed error when the weather location contains multiple cities. | The current deterministic demo never sends this, but agentic mode could. |
| Location typo handling | Add deterministic alias/fuzzy lookup or provider-backed geocoding before weather calls. | Turns `Munih` into a recoverable error or safe suggestion instead of a vague provider failure. |
| Location disambiguation | Ask for clarification or return candidates when a city name is ambiguous. | Avoids returning weather for the wrong city. |

---

## 14. Testing And Verification Improvements

| Topic | What it is | Why it matters |
| --- | --- | --- |
| Boundary integration tests | Test use cases through controlled ports. | Proves orchestration without over-mocking internals. |
| Live demo smoke test | One command to run required questions and record source paths. | Separates live readiness from unit tests. |
| Demo readiness levels | Distinguish source honesty from fully live source success. | `UNAVAILABLE` can be correct but not a full live demo. |
| Dependency-direction scan | ArchUnit/grep for forbidden imports in core packages. | Protects hexagonal boundaries. |
| Log noise reduction | Reduce expected stack traces and Mockito warnings. | Makes real failures easier to spot. |

---

## 15. Observability Improvements

| Topic | What it is | Why it matters |
| --- | --- | --- |
| Correlation across sources | Carry `traceCorrelationId` through MCP, RAG, Ollama, final response. | Debugs one request end-to-end. |
| Structured logs | Route, port, duration, outcome, snippet count. | Searchable and safer than raw logs. |
| Operational metrics | Timeouts, status counts, queue rejections, similarity scores, model latency. | Shows where reliability problems occur. |
| Optional Langfuse-style tracing | Spans for retrieval, tool calls, generation, final response. | Useful for deeper experiments, optional for local-first. |

---

## 16. Architecture And Scope Improvements

| Topic | What it is | Why it matters |
| --- | --- | --- |
| Keep deterministic default | Agentic mode remains opt-in. | Preserves reproducible demo behavior. |
| Avoid over-generalizing | Improve current sources before web browsing/accounts/cloud. | Keeps recruitment scope realistic. |
| Shared-kernel threshold | Add shared module only for stable cross-module types. | MCP boundary currently avoids Java type coupling. |
| ADRs for material changes | Hybrid search, reranking, agentic fallback need ADRs. | Keeps documentation-first discipline. |

---

## 17. Likely Backend Interview Questions

### Why Hexagonal Architecture?

**Answer:** The core problem is orchestration over multiple Knowledge Sources. Ports keep orchestration testable while adapters hide Spring AI, MCP, pgvector, REST Countries, and HTTP/SSE details.

### Why Deterministic Routing Instead Of LLM Tool Choice?

**Answer:** The required demo needs reproducible source paths and no fabricated facts. Deterministic routing makes failure behavior testable. Agentic fallback is reasonable only with limits, timeouts, cancellation, and evals.

### What Happens If A Source Is Down?

**Answer:** The assistant returns a source-unavailable or insufficient-knowledge response and names the failed source. It does not answer country facts, weather observations, or RAG knowledge from model memory.

### How Does The SSE Contract Work?

**Answer:** `trace` shows source outcomes, `token` streams model deltas for synthesis routes, `final` is authoritative, and `error` is only for unexpected post-stream failures. Request validation errors stay as pre-stream HTTP 400 JSON.

### How Is RAG Ingestion Idempotent?

**Answer:** The ingestion flow normalizes extracted text, computes a content hash, skips unchanged content, and replaces chunks for a changed source instead of appending duplicates.

### Why Custom pgvector Schema Instead Of A Generic VectorStore?

**Answer:** The project needs explicit chunk metadata, content-hash based replacement, source URL, chunk index, ingestion timestamp, threshold behavior, and deterministic tests. A custom schema makes those trade-offs visible.

### Why MCP For Countries?

**Answer:** It creates a semantic assistant-facing tool, `country_lookup`, instead of exposing REST Countries directly. The assistant sees a port and compact country facts; REST Countries details remain inside the MCP server.

### What Is The Strongest Part Of The Backend?

**Answer:** Source honesty plus boundaries. The code separates source routing, typed outcomes, adapters, and user-facing response composition.

### What Would You Improve First?

**Answer:** For interview/demo readiness: live readiness checks and weather reliability. For product quality: RAG evals, hybrid search, reranking, then bounded agentic fallback.

### What Is The Main Production Risk?

**Answer:** Local dependency fragility: Ollama models, Docker/pgvector, REST Countries key, WeatherAPI key, subprocess MCP lifecycle, and fixed local ports.

---

## 18. Short Talk Tracks

### Backend Overview

> "The backend is intentionally not an autonomous general agent. It is a local assistant with deterministic source routing, typed ports, and explicit source outcomes. The main quality goal is provenance: every factual answer should be tied to a Knowledge Source or clearly marked unavailable."

### RAG

> "RAG is limited to CDQ Fraud Guard product knowledge. Ingestion extracts, normalizes, chunks, embeds, and stores product text in pgvector. Retrieval embeds the question, applies top-k and threshold, and gives only relevant snippets to the model. No snippets means insufficient product knowledge, not a guess."

### MCP

> "The countries MCP server exposes a semantic `country_lookup` tool, not raw REST Countries. It accepts a country or capital name and returns compact fields the assistant needs. Errors use an `ok/error/hint` envelope so the assistant can recover or refuse honestly."

### SSE

> "SSE is used so the UI can show source trace as it happens and stream model tokens where synthesis is involved. The final event remains authoritative; earlier tokens are provisional."

### Trade-Offs

> "The project favors a reliable, inspectable recruitment demo over broad autonomy. The next step would be bounded agentic fallback, but only after evals show that the local model can select tools reliably."
