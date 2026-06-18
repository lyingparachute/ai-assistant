# ExecPlan — Backend SSE disconnect cancellation

Status: **PLAN-READY** — round-1 critic folded in
Owner: TBD
Source: follow-up from [`improve-chat-interface.md`](improve-chat-interface.md) §5 and §Follow-ups;
README client-abort note (`README.md` ~143); `ChatController` / `ChatStreamTask` partial wiring.
Scope module: `assistant-app` inbound HTTP adapter, orchestration use case(s), outbound Ollama
streaming adapter, MCP invoker boundary checks. **No** Chat Interface changes beyond README
accuracy — client `AbortSignal` is already implemented in `chat-ui`.

## Prerequisites

- [`stream-chat-answers-and-source-usage-trace.md`](stream-chat-answers-and-source-usage-trace.md)
  **landed** — ADR `0009`; `ChatStreamTask`, `SseEmitter` callbacks, `OllamaLlmAdapter` Flux
  subscription disposal on interrupt (contract test exists).
- [`improve-chat-interface.md`](improve-chat-interface.md) **M1 landed** — Stop button aborts fetch;
  README honestly documents backend orphan work today.
- If [`improve-agentic-tool-orchestration.md`](improve-agentic-tool-orchestration.md) is in flight,
  cancellation checks between agentic turns must align with this plan (coordinate M2/M3).
- **Suggested execution order (recruitment):** after agentic M3+ and
  [`sse-trace-agentic-turn-index.md`](sse-trace-agentic-turn-index.md); M1–M2 may defer until agentic
  multi-turn latency matters unless README honesty is the only near-term goal.

## Why now

The Chat Interface Stop button and tab-close both drop the SSE connection. Verified today:

| Layer | Current behaviour |
| --- | --- |
| `ChatController` | `onCompletion` / `onTimeout` / `onError` call `ChatStreamTask.cancel()` |
| `ChatStreamTask` | Sets cancelled flag; interrupts worker thread |
| `OllamaLlmAdapter` | Disposes Flux subscription on interrupt (contract test) |
| README | Still warns backend may continue until timeout — honest but incomplete |

Gaps remain for a recruitment-grade guarantee:

1. **Orchestration** may not check interrupt between deterministic steps or agentic turns.
2. **MCP / RAG / HTTP** outbound calls may run to completion after cancel.
3. **Client disconnect** — `onCompletion` already cancels without `failUnexpected` when no terminal
   (`ChatController.java:60-64`); remaining gap is `ChatStreamTask` catch paths that may still call
   `failUnexpected` if an exception races the cancel flag.
4. No **integration test** proves mid-stream client disconnect stops LLM subscription within a
   bounded time.

This plan makes “Stop frees resources promptly” true at the backend boundary without changing API
facts or SSE event semantics.

## Target state

When the SSE client disconnects (browser Stop, navigation, tab close):

1. `ChatStreamTask.cancel()` propagates into the active use case via a **cancellation handle**
   (worker-thread interrupt + explicit `CancellationSignal` checked at orchestration boundaries).
2. In-flight **`LlmPort.generate`** streaming disposes the reactive subscription (existing behaviour,
   verified under cancel).
3. **Agentic loop** (if landed) exits on next turn boundary with `LoopIncomplete(cancelled)` — no
   further tool calls.
4. **No terminal SSE event** is emitted after the connection is gone (emitter already closed); cancel
   is not surfaced as `error` to a disconnected client.
5. README updated: client abort **stops new port/LLM work** after cancel; in-flight I/O may finish
   (match outbound table §3); no over-promise of instant MCP/HTTP abort.

Deterministic demo answers unchanged when the client stays connected.

## Design (locked decisions)

### 1. Cancellation signal — application port, not Spring type

**This plan owns the type.** Add a narrow **`CancellationHandle`** (single owner — agentic plan
imports it; no parallel interrupt mechanism):

```text
boolean isCancelled()
void throwIfCancelled()   // optional; orchestration checks between steps
```

Constructed in `ChatStreamTask` / controller wiring; passed into
`answer(UserQuestion, AssistantResponseSink, CancellationHandle)` — **locked** explicit handle;
no scattered `Thread.interrupted()` in domain code.

### 2. Orchestration checkpoints

Check cancellation:

- Before each agentic tool-loop iteration (when agentic plan landed).
- Before `LlmPort.generate` and between token batches if the adapter supports it.
- After each port consultation completes (do not start the next source if cancelled).

Do **not** add cancellation checks inside domain value objects.

### 3. Outbound adapters

| Adapter | Rule |
| --- | --- |
| `OllamaLlmAdapter` | Keep Flux `Disposable.dispose()` on cancel/interrupt |
| MCP invoker | Respect interrupt between tool calls; document if in-flight stdio cannot abort |
| RAG / HTTP fetch | Do not start new fetch if cancelled; in-flight fetch may complete |

### 4. SSE edge behaviour

| Callback | Behaviour |
| --- | --- |
| `onCompletion` (client gone, no terminal sent) | Cancel task; **do not** call `failUnexpected` — **already implemented**; M0 audit confirms, no re-work |
| `onTimeout` | Cancel + existing timeout handling (stream still open) |
| `onError` | Cancel; log; fail only if emitter still sendable |
| `ChatStreamTask` catch | If cancelled, suppress `failUnexpected` even when `RuntimeException` races cancel flag |

Align `ChatControllerStreamingTest` with disconnect semantics.

### 5. Observability

Log at DEBUG: cancel reason (`client_disconnect`, `timeout`, `executor_rejected`), correlation id
from sink if available. No new user-facing API fields.

## Scope

- Cancellation handle wiring from `ChatStreamTask` through orchestration entry.
- Checkpoint calls in `AnswerQuestionUseCase` / `AgenticAnswerUseCase` (whichever is live).
- Review + tighten `ChatController` emitter callbacks (no spurious `failUnexpected` on disconnect).
- Verify `OllamaLlmAdapter` dispose path under controller-driven cancel (extend tests if gaps).
- Integration test: start async SSE request, cancel/disconnect mid-use-case, assert use case stops
  and subscription disposed within **2 s** (deterministic stub with dispose latch — not wall-clock
  against live Ollama).
- README + `docs/spec/14-assistant-api-contract.md` note on client disconnect (behavioural, not
  new events).
- `docs/ai/streaming-sse-api.md` limitation update.

## Out of scope / non-goals

- New SSE event type for cancel (`cancelled` event) — disconnected clients cannot receive it.
- Propagating cancel into Ollama server process kill (JVM stop is sufficient for recruitment scope).
- Changing Chat Interface Stop UX (`improve-chat-interface.md` owns client abort).
- Cancellation across unrelated requests / global executor shutdown policy rewrite.
- MCP SDK upstream cancellation API design — best-effort between calls only.

## Invariants (must hold)

- Honesty model unchanged: connected clients still receive authoritative `final` or `error`.
- No new facts in answers; cancel does not synthesize responses.
- Domain/application code does not import `SseEmitter` or reactor types.
- Existing demo e2e passes without client mid-stream abort (no regression on happy path).

## Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Interrupt checks scattered | Single `CancellationHandle` passed per request |
| MCP stdio call not interruptible | Document; checkpoint before next call |
| Race: terminal sent then disconnect | `onCompletion` already guards with `terminalEventSent()` |
| Agentic + deterministic diverge | One handle type; both use cases share checkpoint helper |

## Definition of Done

- [ ] M0 audit: checkpoint list distinguishes **already done** (emitter `onCompletion` cancel-only,
  Ollama dispose contract test) vs **missing** (orchestration checkpoints, `ChatStreamTask` race,
  integration proof).
- [ ] Cancellation handle wired; cancelled request does not start a second port call.
- [ ] Client disconnect integration test green (mid-stream abort; dispose within 2 s via stub latch).
- [ ] Negative AC: after cancel, no second `recordSourceOutcome` and no `final` on sink.
- [ ] `OllamaLlmAdapter` dispose verified under controller cancel (test output pasted).
- [ ] `ChatStreamTask` catch: cancelled + `RuntimeException` does not call `failUnexpected`.
- [ ] Signature migration grep-clean: all `answer(UserQuestion, AssistantResponseSink)` call sites
  and test doubles updated to 3-arg form.
- [ ] Agentic loop (if landed): cancel between turns — one unit test.
- [ ] README client-abort note updated to match verified behaviour (no new port calls; in-flight I/O
  may finish).
- [ ] `./mvnw -pl assistant-app test` — paste focused test class output.
- [ ] Demo e2e still green — **optional** `./mvnw -pl e2e-tests verify -P e2e`; document skip if
  live stack unavailable (cancel behaviour proven by assistant-app integration test).

## Milestones

### M0 — Audit gate (stop if unclear)

1. Trace cancel path: `ChatController` → `ChatStreamTask` → use case → `LlmPort` / ports.
2. **Audit split (required output):**
   - **Already done:** `onCompletion` cancel-only when no terminal; Ollama Flux dispose on interrupt
     (cite `ChatController.java`, contract test class).
   - **Missing / in scope:** orchestration checkpoint calls; `ChatStreamTask` catch race;
     integration test with dispose latch.
3. Document checkpoint list in plan or `docs/ai/streaming-sse-api.md` draft section.
4. Run existing `ChatControllerStreamingTest` + `OllamaLlmAdapterContractTest`; paste baseline.

**Verify:** audit table approved; baseline tests green. **Stop if cancel path cannot reach
orchestration without API signature change — resolve in M1 design.**

### M1 — Handle + orchestration checkpoints

- Introduce per-request cancellation handle; thread interrupt sets cancelled.
- Add checks before port calls and before LLM synthesis.
- Unit tests: cancelled before use case → no port invocations.

**Verify:** paste test output for new cancellation tests.

### M2 — SSE disconnect integration

- Adjust `ChatController` callbacks per design §4 (focus on `ChatStreamTask` catch race — emitter
  `onCompletion` already correct).
- MockMvc or WebTestClient test: async SSE started, client disconnect simulated, worker stops within
  **2 s** via dispose latch; no terminal event after cancel.

**Verify:** paste integration test output.

### M3 — Agentic alignment (gate: agentic landed)

- If `AgenticAnswerUseCase` exists: cancel between turns → `LoopIncomplete`.
- One test with stubbed `LlmToolCallPort`.

**Verify:** paste test output. **Skip milestone with documented reason if agentic not landed.**

### M4 — Docs + land

- README, spec 14 behavioural note, streaming AI doc.
- Mark plan **landed — `<sha>`**.

## Documentation impact

- `README.md` — revise client-abort limitation to verified cancellation behaviour.
- `docs/spec/14-assistant-api-contract.md` — short “client disconnect” subsection (no schema change).
- `docs/ai/streaming-sse-api.md` — cancellation verification evidence.
- Cross-link from `improve-chat-interface.md` follow-ups.

## Coordination

| Dependency | Owner | Rule |
| --- | --- | --- |
| `CancellationHandle` type | **This plan (M1)** | Agentic plan imports; no duplicate interrupt mechanism |
| Agentic loop checkpoints | [`improve-agentic-tool-orchestration.md`](improve-agentic-tool-orchestration.md) §7 | Uses shared handle; defers wiring to this plan |
| Trace index emission | [`sse-trace-agentic-turn-index.md`](sse-trace-agentic-turn-index.md) | Serial: agentic M3+ before this plan M1–M2 unless README-only |

## Round-1 critic resolutions (authoritative; supersede the body where in conflict)

- **[B2R1-1]** M0 audit **must split already-done vs missing** — `onCompletion` cancel-only is
  implemented; do not re-implement emitter callbacks.
- **[B2R1-2]** **`CancellationHandle` single owner** — this plan M1 defines; agentic plan imports.
- **[B2R1-3]** Integration AC bound: dispose within **2 s** via deterministic stub latch (not flaky
  wall-clock).
- **[B2R1-4]** DoD: grep-clean 2-arg → 3-arg `answer(...)` migration across call sites and test doubles.
- **[B2R1-5]** README target softened: no new port calls after cancel; in-flight MCP/HTTP may finish.
- **[B2R1-6]** Demo e2e optional in DoD; assistant-app integration test is the proof.
- **[B2R1-7]** Negative AC: no second `recordSourceOutcome` / no `final` after cancel.
- **[B2R1-8]** `ChatStreamTask` catch race: cancelled + exception must not `failUnexpected`.
- **[B2R1-9]** M1–M2 may defer until agentic M3+ unless README honesty is the only near-term goal.
