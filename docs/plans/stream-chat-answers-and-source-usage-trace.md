# ExecPlan — stream chat answers + live Source-Usage Trace

Status: completed locally — not landed (M6 docs + hermetic verify; live re-capture skipped — stack down)
Owner: TBD
Source: handoff "wow" ideas (token streaming; per-turn source trace in the Chat Interface);
honesty guarantees in `CLAUDE.md` §8 and `CONTEXT.md`; verified live state of `/api/chat`.
Scope module: `assistant-app` (inbound HTTP adapter, application orchestration, outbound
Ollama adapter), `chat-ui` (Astro Chat Interface), `e2e-tests` (client transport migration),
`scripts/capture-demo-answers.sh` (demo capture must keep working).

## Prerequisites

- `docs/plans/backend-hygiene.md` **landed** — Boot `3.5.12`, Commons Lang, Lombok conventions
  (`@Slf4j`, `@UtilityClass`, `@RequiredArgsConstructor`, selective `@Builder`) in place on the
  adapter/orchestration code this plan touches. Do not start SSE work on the pre-hygiene stack.
- Demo path still green (`docs/demo/final-answers.md`, `./mvnw -pl e2e-tests verify -P e2e`).
- ADR `docs/adr/0009-stream-assistant-api-over-sse.md` **accepted** (documentation-first;
  status moves from Proposed to Accepted in the same change that starts M1).
- Re-verify Spring AI streaming assumptions (`ChatModel.stream`, delta chunk semantics) against
  the **post-hygiene** pinned Spring AI version before M1 implementation.

## Why now

The assistant answers correctly but the Chat Interface feels inert: the reviewer waits on a
blank panel, then the whole answer appears at once, and the only visible evidence of *which
real sources were used* is a static list of source cards rendered after the fact. Two changes
turn the existing honesty machinery into a visible, responsive experience without changing a
single fact the assistant produces:

1. **Streamed Answer** — deliver the answer incrementally so the reviewer sees it form.
2. **Live Source-Usage Trace** — show each Knowledge Source as its outcome is determined
   (`USED` / `UNAVAILABLE` / `INSUFFICIENT`), so the reviewer literally watches the assistant
   consult REST Countries, the weather source, and RAG — and watches it refuse to invent when
   a source fails. This makes the project's core promise (no guess presented as fact) *visible*.

Verified facts this plan rests on (source verification 2026-06-17; re-run after hygiene bumps):

- The LLM is invoked through a single shared `synthesize` helper (`AnswerQuestionUseCase:191`,
  `llmPort.generate` at `:197`) reached from only 2 of 6 routes (`synthesizePlace`,
  `handleCdqProduct`); the other four routes compose deterministically via `ResponseComposer`
  with no LLM call. So only those two routes have tokens to stream.
- For `CdqProduct`, RAG retrieval runs first; `NoRelevantKnowledge` (`:212`) and
  `SourceUnavailable` (`:216`) short-circuit without calling the LLM. Model synthesis only runs
  over already-established grounding.
- `LlmPort.generate` is blocking and returns a sealed `LlmResult = Success(text) |
  SourceUnavailable`; the blank/failed → SourceUnavailable guarantee lives in
  `OllamaLlmAdapter` (`:38`, `:43`).
- `ChatModel` **extends** `StreamingChatModel` in Spring AI 1.1.x (`javap` on pinned BOM) — the
  existing `ChatModel` field in `OllamaLlmAdapter` already exposes `.stream(Prompt)`; no bean
  type change required. `reactor-core` is already a **transitive** compile dependency via
  `spring-ai-starter-model-ollama` (no new top-level dependency).
- `/api/chat` in-repo consumers (all must migrate in this change):
  `chat-ui/src/lib/api.ts`, `e2e-tests/.../AssistantApiClient.java`,
  `scripts/capture-demo-answers.sh`, `docs/spec/08-demo-plan.md` curl examples, and
  `assistant-app/.../ChatControllerIntegrationTest.java`. No external clients.

## Target state

`POST /api/chat` returns `text/event-stream`. As the assistant answers one question it emits,
in order:

- a `trace` event per Knowledge Source the moment that source's outcome is known
  (`{ type, status }` — **`type`** matches today's `SourceResponse.type` /
  `chat-ui` `SourceType`: `countries_facts | weather_observation | rag_knowledge |
  model_synthesis`; **`status`** ∈ `USED | UNAVAILABLE | INSUFFICIENT`);
- on the two synthesis routes, `token` events (`{ text }`) carrying live answer deltas;
- exactly one terminal `final` event carrying today's authoritative `ChatResponse` payload
  (`answerText`, `sources`, `traceCorrelationId`) — **semantically equal** to the current JSON
  success body (same fields and values; JSON key order not guaranteed);
- at most one `error` event for unexpected infrastructure failures after the stream has opened
  (see design §8).

The Chat Interface renders the trace as a clean step timeline, appends tokens live, then
reconciles to the `final` event (the authoritative answer always wins; partial tokens never
stand). Deterministic routes emit `trace` events + a single `final`, with no `token` events.

**UX note (synthesis routes):** `model_synthesis` `trace` is emitted only after `generate()`
returns, so tokens may appear before synthesis shows `USED`. The answer area labels streamed
text as provisional (e.g. helper copy “Forming answer…”) until `final` reconciles; `final`
always wins.

Streaming is **purely additive observation**: provenance and honesty validation are still
built by `ResponseComposer` from the complete, validated `LlmResult`. No fact, source label,
or source-unavailable decision changes.

## Design (locked decisions)

1. **Streaming-only endpoint, terminal event authoritative.** `POST /api/chat` is SSE. There
   is no parallel JSON endpoint (delete-before-add). The terminal `final` event payload is
   semantically today's `ChatResponse`; all in-repo consumers migrate in this change
   (documentation-first).

2. **Four-event protocol.** `trace`, `token`, `final`, `error` (named SSE events, JSON `data:`).
   Expected source failures — including every Source-Unavailable Response — are normal **`final`**
   answers with structured `sources[]`, never `error` events. Request validation (`@Valid`,
   blank question, unreadable JSON) stays **HTTP 400 JSON before the stream opens** — handler
   never returns `SseEmitter`. `HttpExceptionHandler` methods set `Content-Type:
   application/json` explicitly on 400/500 bodies so the SSE `produces` type does not negotiate
   them away.

3. **Output-sink port (application-owned), domain-typed.** New port `AssistantResponseSink` in
   `orchestration` with **locked methods** (names are domain vocabulary, not SSE event names):

   | Method | When | Maps to SSE |
   | --- | --- | --- |
   | `void recordSourceOutcome(SourceType type, SourceContributionStatus status)` | Each Knowledge Source resolves | `trace` |
   | `void appendAnswerToken(String delta)` | Each non-blank LLM delta | `token` |
   | `void complete(ConversationTurn turn)` | Successful path (includes honest source-unavailable answers) | `final` |
   | `void failUnexpected(String errorCode, String message)` | Unexpected failure after stream open | `error` |

   `SourceType` is a small domain enum mirroring today's four `AnswerSource` / `SourceResponse`
   type strings (not internal `Port` names). The inbound HTTP adapter implements
   `AssistantResponseSink` per request and is the only place that serializes SSE. No
   `adapters.inbound.http` type appears on the port signature.

4. **Use-case entry point (request-scoped sink).** Replace the public API:

   ```text
   ConversationTurn answer(UserQuestion question, AssistantResponseSink sink)
   ```

   - The **controller** constructs one sink implementation per HTTP request, then submits
     `() -> answerQuestionUseCase.answer(question, sink)` to the bounded executor.
   - The sink is **never** a Spring bean (request-scoped by construction, not `@Scope`).
   - Remove the old `answer(UserQuestion)` overload — no dual path. Tests call
     `answer(..., new RecordingAssistantResponseSink())` or a test double.
   - `ChatController` alone owns `SseEmitter` lifecycle; the use case stays synchronous and
     blocking on the worker thread.

5. **Narrow `TokenSink` on `LlmPort`.** `LlmPort.generate(PromptContext, TokenSink)` returns
   the authoritative `LlmResult`; `TokenSink` is a domain `@FunctionalInterface` in `llm`.
   Single call site: `synthesize` (`AnswerQuestionUseCase:197`). Remove the no-sink
   `generate(PromptContext)` outright. `OllamaLlmAdapter` uses `chatModel.stream(prompt)` via a
   **cancellable subscription** (`Disposable` + `CountDownLatch`; dispose path always counts down).
   Reject `flux.toStream()` (no cancellation handle). Apply `Flux.timeout` for whole-stream budget.
   M1 verifies delta-not-cumulative chunk semantics for the pinned Spring AI version. Skip
   null/blank chunk deltas before pushing to `TokenSink`.

6. **Trace status from resolved result variants.** `AnswerSource` objects are built only in
   `ResponseComposer` at route end — they cannot drive live trace. At each route arm the use case
   calls `recordSourceOutcome` when that source's result *variant* resolves:
   `Success` → `USED`; `SourceUnavailable`/`ToolError` → `UNAVAILABLE`;
   `NoRelevantKnowledge` → `INSUFFICIENT`. `trace.portInvoked(...)` is logging only, never a
   trace trigger. `model_synthesis` is recorded only after `generate()` returns (after tokens).
   Internal `Port` names are never exposed.

7. **Country lookup consolidation.** `handlePlaceSynthesis` (`:159`) must emit `countries_facts`
   trace — **consolidate** inline `countriesPort.lookupCountry` into the shared `lookupCountry`
   helper (`:246`) so trace emission lives in one place. `Unsupported` emits **zero** `trace`
   events and one `final` with empty `sources`.

8. **Pre-stream vs post-stream failure (replaces sync HTTP 500 for in-flight work).**

   | Phase | Failure | HTTP | Body |
   | --- | --- | --- | --- |
   | Pre-stream | Bean validation, unreadable JSON | 400 | JSON `ApiErrorResponse` |
   | Pre-stream | Failure before `SseEmitter` returned to client (cannot happen in normal flow if emitter returned immediately) | — | — |
   | Post-stream | Use-case exception, timeout, adapter failure, cancellation | **200** `text/event-stream` | `error` event then stream close |

   **Migration:** `ChatControllerIntegrationTest.unexpectedDomainFailureReturnsServerError...`
   is **rewritten** (or replaced by `ChatControllerStreamingTest`) to expect **200 + `error`
   event**, not HTTP 500, when the use case throws on the worker thread. Pre-stream validation
   tests stay HTTP 400 JSON.

9. **Bounded async with cancellation.** `SseEmitter` + bounded executor (fixed pool, bounded
   queue). New typed config record **`AssistantChatProperties`** (`assistant.chat.*`):
   `stream-timeout-seconds` (strictly larger than `assistant.llm.timeout-seconds`) and executor
   `pool-size` + `queue-capacity` (all `@Positive`, `@DefaultValue` documented in yml). Wire
   executor bean in inbound HTTP config; no scattered `@Value`. On timeout/error/disconnect:
   dispose Flux subscription, release worker latch, emit `error` or complete once (never both).
   Configure `OllamaApi.builder().webClientBuilder(...)` on the **chat** `OllamaApi` bean only
   (embedding bean keeps RestClient-only config).

10. **Package structure unchanged.** Streaming code in `orchestration`, `llm`,
    `adapters/inbound/http`, `adapters/outbound/ollama`. Per-feature restructure is a later plan.

## Scope

- `AssistantResponseSink` port + `SourceType` enum; `AnswerQuestionUseCase.answer(question, sink)`;
  per-route `recordSourceOutcome` / `appendAnswerToken` / `complete`; consolidate PlaceSynthesis
  country lookup through `lookupCountry`.
- `TokenSink`; `LlmPort.generate(PromptContext, TokenSink)`; streaming `OllamaLlmAdapter`; WebClient
  timeout on chat `OllamaApi`. **M1 `LlmPort` migration checklist (grep `implements LlmPort`):**
  `OllamaLlmAdapter` (streaming adapter), `StubLlmPort` (add `TokenSink` param — ignore or record
  deltas in tests), `ChatPathPortStubs.stubLlmPort()` lambda, any `@Bean LlmPort` in test configs.
- `AssistantChatProperties` + executor bean + binding test.
- Inbound: `ChatController` → `SseEmitter`; `SseAssistantResponseSink` implements port + maps to
  SSE; bounded executor; `ChatHttpMapper.toChatResponse(ConversationTurn)` for `final` payload.
- **Tests (assistant-app):** `OllamaLlmAdapterContractTest` (streaming); sink route matrix unit
  tests; **`ChatControllerIntegrationTest` rewritten for SSE** (or superseded by
  `ChatControllerStreamingTest` with `MockMvc` async SSE assertions); CORS preflight test updated
  if needed; pre-stream 400 JSON tests retained.
- **CORS:** streaming `fetch` sends only `Content-Type` (no custom `Accept` unless
  `allowedHeaders` is explicitly extended — test the negative case).
- **chat-ui:** `api.ts` SSE reader + parser unit test; `index.astro` trace timeline + safe token
  append; reconcile to `final`; zero-trace `Unsupported` layout.
- **e2e-tests:** `AssistantApiClient.chat()` parses SSE to `final` `JsonNode`; focused test that
  parsed `final` is semantically equal to a captured pre-migration JSON for one question.
- **`scripts/capture-demo-answers.sh`:** migrate `ask()` to read SSE (`curl -N`) and extract the
  `final` event JSON before `python3` round-trip — demo re-capture must work without manual edits.
- **Docs:** ADR `0009` Accepted; rewrite `docs/spec/14-assistant-api-contract.md`; note in
  `docs/spec/05-architecture.md` §6/§13; confirm `CONTEXT.md` glossary; README; `08-demo-plan` curl `-N`.

## Out of scope / non-goals

- Package restructure, agentic orchestration, conversational memory, parallel JSON endpoint.
- Facts, routing, source labels, or honesty decision changes.
- New external sources or providers.

## Invariants (must hold)

- Honesty: provenance from complete `LlmResult` via `ResponseComposer`; `final` replaces partial
  tokens; RAG before synthesis tokens; no fabrication on failure.
- Trace truth: `trace` `{type,status}` multiset equals `final.sources` multiset for every route.
- Token safety: text DOM APIs only in chat-ui for streamed tokens.
- Boundedness: stream always ends with exactly one `final` **or** one `error` (post-stream).
- Architecture: no Spring AI / reactor / SSE types in `{orchestration,llm,question,rag,tools}`.
- Contract: `RequiredDemoQuestionsIT` assertions unchanged after e2e client migration.

## Risks and open questions

- **e2e + capture script SSE parsing** — load-bearing; mitigated by focused parser tests and
  semantic equality check against captured JSON.
- **Chat-ui SSE reader** — mitigated by isolated parser test + reconcile to `final`.
- **Token-then-failure** — terminal `final` clears provisional tokens; use-case test required.
- **Spring AI patch after hygiene** — re-verify delta chunk semantics in M1.

## Definition of Done

- [ ] `POST /api/chat` streams `text/event-stream`; synthesis question shows `token` + authoritative
      `final` (paste live output). _2026-06-18: live stack down — boundary fixture in demo-run-log._
- [ ] Deterministic question shows `trace` + `final`, no `token` (paste live output).
      _2026-06-18: boundary fixture `germany-capital-sse-stream.txt` in demo-run-log._
- [ ] PlaceSynthesis emits `countries_facts` `trace`; Atlantis emits zero `trace` + empty `sources`
      (test + UI).
- [ ] Streamed XSS probe token renders as literal text (frontend test).
- [ ] All six routes: `trace` type/status multiset equals `final.sources` (unit test matrix).
- [ ] `model_synthesis` `trace` only after `generate()` returns (unit test).
- [ ] Token-then-`SourceUnavailable`: ≥1 `token`, then `final` with no streamed text in answer (test).
- [ ] Post-stream unexpected failure → `error` event (test). Pre-stream blank question → HTTP 400
      JSON + `Content-Type: application/json` (test). CORS preflight without custom `Accept` (test).
- [ ] Cancellation / no thread leak after N requests (test or live note).
- [ ] M1 chunk semantics: `join(deltas) == Success.text()` on same pushed deltas (test).
- [ ] Boundary grep clean over orchestration/llm/question/rag/tools (pattern in prior plan).
- [ ] `AssistantApiClient` + `capture-demo-answers.sh` extract `final` JSON; e2e 5/5 green (paste).
- [ ] `ChatControllerIntegrationTest` (or replacement) covers SSE success, 400 pre-stream, post-stream
      `error` — **not** HTTP 500 for worker-thread use-case throw.
- [ ] Full `assistant-app` + `countries-mcp-server` suites green (paste totals).
- [x] ADR `0009` **Accepted**; spec 14 rewritten; spec 05 note; CONTEXT confirmed; README + demo curl `-N`.

## Milestones

- [ ] **M1 — `TokenSink` + streaming `OllamaLlmAdapter` + WebClient timeout.** Stubbed
      `StreamingChatModel` contract test; all `LlmPort` implementers updated; grep checklist green.
- [ ] **M2 — `AssistantResponseSink` + `answer(question, sink)` + route emissions + lookup
      consolidation.** Stubbed-sink tests for all six routes + token-then-failure + trace/final parity.
      **Migrate `AnswerQuestionUseCaseTest`** (every `answer(...)` call passes a recording sink).
- [ ] **M3 — Inbound SSE adapter** (`SseEmitter`, executor, `SseAssistantResponseSink`, four-event
      protocol). Rewrite `ChatControllerIntegrationTest` / add streaming web tests.
- [ ] **M4 — `e2e-tests` + `capture-demo-answers.sh`.** SSE client + script migration; semantic
      equality focused test; `RequiredDemoQuestionsIT` green.
- [ ] **M5 — `chat-ui`.** Parser test, trace timeline, safe tokens, `final` reconciliation.
- [x] **M6 — Live verification + docs.** Paste live SSE samples; ADR Accepted; spec/README/demo plan.

## Documentation impact

- ADR `0009` — Accepted (not “added”).
- `docs/spec/14-assistant-api-contract.md` — SSE protocol; `trace` uses `type` + `status`.
- `docs/spec/05-architecture.md`, `CONTEXT.md`, `README.md`, `docs/spec/08-demo-plan.md`.

## Round-2 critic resolutions (authoritative; supersede body on conflict)

- **[P9R2-1]** `scripts/capture-demo-answers.sh` in scope + DoD — demo re-capture is a contract consumer.
- **[P9R2-2]** Post-stream failures → `error` SSE event (HTTP 200), not HTTP 500; rewrite controller tests.
- **[P9R2-3]** `AssistantResponseSink.complete(ConversationTurn)` — single completion type; `final` via `ChatHttpMapper`.
- **[P9R2-4]** `answer(UserQuestion, AssistantResponseSink)` request-scoped; remove parameterless `answer`.
- **[P9R2-5]** Trace payload field **`type`** (not `source`) — matches `SourceResponse` / `types.ts`.
- **[P9R2-6]** `final` JSON **semantically equal**, not byte-identical.
- **[P9R2-7]** M4–M6 split: e2e/script before chat-ui; docs last.
- **[P9R2-8]** M1 explicit `LlmPort` implementer checklist.
- **[P9R2-9]** `AssistantChatProperties` (`assistant.chat.*`) + binding test for stream timeout and
  executor sizing (refactor-3b record pattern).
- **[P9R2-10]** CORS locked: **do not** send custom `Accept`; rely on default fetch accept + POST
  `Content-Type` only; preflight test is the negative guard.

## Round-3 critic resolutions (post subagent review)

- **[P9R3-1]** `ChatModel extends StreamingChatModel` in Spring AI 1.1.x — **confirmed** via `javap`;
  no `OllamaLlmAdapter` field-type change. `reactor-core` already transitive — not a new dependency.

## Follow-ups (separate plans, not this one)

- Package restructure (`domain`/`infrastructure`); platform migration Boot 4 / Spring AI 2.
