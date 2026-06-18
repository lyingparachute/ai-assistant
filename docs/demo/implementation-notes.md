# Implementation Notes — Phase 7 Demo Capture (+ countries unblock)

Running log of decisions, deviations, and tradeoffs made while driving Phase 7 to completion.
Started 2026-06-17.

## Context

Phase 7 (`docs/plans/phase-7-demo-capture.md`) was blocked. The 2026-06-16 capture hit three
upstream blockers (REST Countries v3.1 deprecated, `WEATHER_API_KEY` unset, RAG not ingested).
This session resolves what is resolvable and tracks what is not.

## Decisions (user-directed)

- **Countries v3.1 deprecation** → write a migration plan/ADR FIRST, no code until approved.
  Rationale: editing `countries-mcp-server` is outside Phase 7 scope (it is Phase 2 code), and the
  repo is documentation-first. A plan keeps code and docs from drifting.
- **Weather** → user provides `WEATHER_API_KEY` (WeatherAPI.com free tier). No fabrication.
- **Commits** → one meaningful commit per logical unit, matching existing `feat(...)/docs:` style.
  No per-milestone commit spam.

## Environment verified (2026-06-17)

- Ollama installed, `qwen3:4b` + `nomic-embed-text` present.
- Docker running, `assistant-pgvector` (pgvector/pgvector:pg17) up on 5432.
- `WEATHER_API_KEY` unset at session start.
- `countries-mcp-server` REST Countries base URL = `https://restcountries.com/v3.1` (deprecated).

## Progress

### 2026-06-17 — investigation + Phase 7 unblock prep

Live state verified against the running stack (port 8080):

| Demo question | Path | Status | Blocker |
| --- | --- | --- | --- |
| Germany capital | countries | UNAVAILABLE | REST Countries v3.1 dead |
| Munich weather | weather | blocked | `WEATHER_API_KEY` (now set) |
| Capital-of-Germany weather | combined | blocked | both |
| Berlin synthesis | place-synthesis | UNAVAILABLE | needs live country fact |
| CDQ Fraud Guard | cdq-product | **WORKS** (`rag_knowledge: USED`, real snippets) | none |
| Atlantis | unsupported (honesty guard) | returns no-route | by design |

- **RAG already ingested**: `rag_chunks` has 9 real CDQ rows (source
  `https://www.cdq.com/products/cdq-fraud-guard`). The 2026-06-16 "count 0" blocker
  is resolved; CDQ showcase verified live.
- **Weather key**: user supplied a WeatherAPI.com key; validated live (Munich 20.1°C,
  2026-06-17 09:15). Stored in gitignored `.env`.

#### Local secrets loading (added this session)

- Added committed `.env.example` (template, no secrets) and gitignored `.env` (real
  keys). Verified `git check-ignore .env` matches `.gitignore:9`.
- `scripts/start-assistant.sh` now sources `$REPO_ROOT/.env` (set -a) so keys reach
  the assistant JVM and, via `StdioMcpToolInvoker` weather passthrough, the weather
  subprocess. Rationale: gives the recruiter a documented way to run the live demo
  with their own free keys without any secret in the repo.

#### Countries v3.1 → v5 (planned, not implemented; user chose "plan first")

- v3.1 permanently dead. v5 verified: base `https://api.restcountries.com/countries/v5`,
  name lookup `/names.common/{value}`, capital `/capitals/{value}`, requires
  `Authorization: Bearer`, free tier 500 req/month.
- **Population — docs wrong, live contract authoritative (decision reversed).** The
  v5 field-reference docs say `population` is "dynamic, no read, no search". The
  critic flagged this and I initially planned to drop population (user chose that).
  Then the user asked to double-verify the contract with the real key. A live
  authenticated call returned `"population":83497147` — population IS readable via
  `response_fields`. So population is **retained**; no domain change. This is exactly
  why we verify live instead of trusting docs (`CLAUDE.md`: verify before acting).
- **Verified live v5 contract (2026-06-17, real key, Germany + France):**
  base `https://api.restcountries.com/countries/v5`; name lookup
  `/names.common/{value}`, capital `/capitals/{value}`, with
  `response_fields=names.common,capitals,region,population`; `Authorization: Bearer`;
  nested body `data.objects[].{names.common, region, population, capitals[].name +
  capitals[].attributes.primary}`; not-found = HTTP 200 with empty `data.objects`;
  no auth = HTTP 401. Migration is now adapter + config only.
- Env var name: user chose `REST_COUNTRIES_API_KEY` (shorter than the MCP-prefixed
  form). Stored in `.env`.
- Plan critic review (fresh subagent) also caught: wrong v5 endpoint paths, a false
  "mirror weather passthrough" claim (passthrough is weather-only/hardcoded), and an
  `@NotBlank`-vs-graceful-failure contradiction. All corrected in plan/ADR.
- Plan critic review (fresh subagent) caught: wrong v5 endpoint paths, an
  unverified "confirmed" success-body claim, a false "mirror weather passthrough"
  claim (passthrough is weather-only/hardcoded), and an `@NotBlank`-vs-graceful-
  failure contradiction. All corrected in the plan/ADR before commit.

#### Countries v5 migration — implemented and live-verified (2026-06-17)

- countries-mcp-server adapter + config migrated to v5; assistant-app env passthrough
  generalized to a config-driven per-server `env-passthrough` list (removed the
  weather-only hardcoding). Subagent-driven: implement → critic + clean-code review →
  fix → re-review, all green (countries 30 tests, assistant-app 223 tests).

#### Three latent bugs found ONLY via live end-to-end verification

Unit tests (219) all passed but the app could not actually serve requests. Live runs
surfaced three pre-existing/integration bugs:

1. **Startup failure (pre-existing on `main` `96edca4`).** `StdioMcpToolInvoker` is a
   `@Component` with two unannotated constructors → Spring fell back to a no-arg
   constructor → `BeanInstantiationException` at boot. The bean is `@Profile("!test")`
   so no test instantiated it via Spring. Fix: `@Autowired` on the production
   constructor + a regression test driving `AutowiredAnnotationBeanPostProcessor`.
2. **`isError` NPE (pre-existing).** `StdioMcpToolInvoker.invoke` did
   `new McpToolResponse(text, result.isError())`; the MCP SDK's `isError()` is a boxed
   `Boolean` that is null when a server omits the field. The semdin weather server
   omits it, so EVERY successful weather call NPE'd and was reported as
   "MCP tool call failed" — a real 23°C reading turned into source-unavailable. Fix:
   `Boolean.TRUE.equals(result.isError())` (MCP spec: absent = not error). Also added
   the previously-missing `log.warn` of the cause, which had hidden this bug.
3. (diagnostic) The weather failure was opaque because MCP failures were never logged;
   adding that logging is a kept observability improvement.

#### Live verification results (2026-06-17, real keys, fresh stack)

All six demo questions exercised against the running stack:
- Germany capital → "Berlin" (countries_facts USED, v5: region Europe, pop 83,497,147)
- Munich weather → 23.2°C (weather_observation USED, live, timestamped)
- Capital-of-Germany weather → Berlin 19.1°C (countries + weather USED)
- Berlin → country fact + model_synthesis USED
- CDQ Fraud Guard → grounded RAG answer, 5 snippets (rag_knowledge USED)
- Atlantis → honest "no matching source route" (honesty guard; no fabrication)

#### Agent-improvement research

Consulted the `nlm` AI_DEVS notebook for best practices on a bounded agentic
tool-calling loop (Agent Harness, max 5–15 iterations, per-tool timeouts +
cancellation, typed `{result, recovery_hints, diagnostics}`, reasoning-first schemas,
deterministic gating for irreversible actions, when to keep deterministic routing).
Captured in the improvement plan `docs/plans/improve-agentic-tool-orchestration.md`.

### 2026-06-17 — next "wow" chosen: streaming + live source-usage trace (planned)

User picked streaming responses + per-turn trace over the agentic plan (which stays
proposed). Brainstormed + grilled against the glossary; key decisions:

- **Terminology fix (glossary).** "tool-call trace" mislabels half of what it shows —
  per `CONTEXT.md`, a Tool is MCP-exposed only, so RAG (pgvector) and Ollama synthesis
  are NOT tools. Renamed to **Source-Usage Trace** (Knowledge Sources + USED/UNAVAILABLE/
  INSUFFICIENT). Added `Source-Usage Trace` and `Streamed Answer` to `CONTEXT.md`.
- **Independent subagent verification (source + context7 for Spring AI 1.1.2):** LLM runs
  on only 2 of 6 routes; RAG grounding precedes synthesis; `LlmResult` validation in
  `OllamaLlmAdapter`; `ChatModel extends StreamingChatModel` (`Flux<ChatResponse>
  stream(Prompt)`, Ollama-supported, delta = `getOutput().getText()`); `/api/chat`'s only
  consumers are the Chat Interface, the e2e client, and the demo curl. All claims PASS.
- **Decisions locked:** SSE-only `POST /api/chat` (no orphan JSON endpoint — user noted the
  JSON path would have no consumer); terminal `final` event = today's authoritative
  `ChatResponse`; four events `trace|token|final|error`; application output port
  `AssistantResponseSink` + narrow domain `TokenSink` on `LlmPort` with reactor confined to
  the Ollama adapter via `flux.toStream()`; bounded async `SseEmitter`; trace events fire at
  outcome time (no in-progress noise, no `Port` leak). Honesty: additive observation only,
  authoritative final wins, no fabrication.
- **Scope split (user-directed):** streaming first in the **current** package structure; a
  separate later plan does the `domain`/`infrastructure` per-feature restructure (own ADR
  amending `0005` + rewrite of spec 05 §4 + hexagonal skill).
- **User Java micro-style (apply in all slices):** `final var`, no empty-string literals
  (named constants / `StringUtils`), `StringUtils` for string checks, `Objects.isNull/
  nonNull` for null checks.
- Artifacts: plan `docs/plans/stream-chat-answers-and-source-usage-trace.md`, ADR
  `0009-stream-assistant-api-over-sse.md` (Proposed).
