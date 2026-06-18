# ExecPlan — SSE trace agentic turn index (API ADR)

Status: **PLAN-READY — BLOCKED on agentic M3+** — round-1 critic folded in; M1 backend emission
starts only after [`improve-agentic-tool-orchestration.md`](improve-agentic-tool-orchestration.md)
M3+ lands (or fold emission into agentic M5)
Owner: TBD
Source: follow-up from [`improve-chat-interface.md`](improve-chat-interface.md) §11 and §Follow-ups;
[`improve-agentic-tool-orchestration.md`](improve-agentic-tool-orchestration.md) §10 tracing.
Scope module: `assistant-app` (orchestration trace emission, HTTP mapper), `chat-ui` (trace
display grouping), `e2e-tests` (consumer tolerance), `docs/adr/` + `docs/spec/14-assistant-api-contract.md`.
**Additive** API change — backward-tolerant consumers ignore unknown fields.

## Prerequisites

- ADR [`0009-stream-assistant-api-over-sse.md`](../adr/0009-stream-assistant-api-over-sse.md)
  **Accepted** — current `trace` payload `{ type, status }`.
- [`improve-agentic-tool-orchestration.md`](improve-agentic-tool-orchestration.md) **M3+ landed**
  (hard gate for M1 backend) — multi-turn tool loop emits multiple consultations before synthesis.
  `AgenticAnswerUseCase` must exist in codebase before M1 starts.
- [`improve-chat-interface.md`](improve-chat-interface.md) **M2a landed** — trace timeline UI exists
  (M3 polish not required; M2 may proceed with mocked SSE payloads).

## Why now

Deterministic routes emit at most one trace step per Knowledge Source — flat timeline is enough.
**Agentic orchestration** may consult multiple capabilities across several LLM turns before
synthesis (`improve-agentic-tool-orchestration.md` §10: turn index in `AssistantRequestTrace`).

Without a wire-level turn index, the Chat Interface Source-Usage Trace shows a flat list — reviewers
cannot see **which agentic iteration** triggered each consultation. That hides the bounded loop
story the recruitment task is meant to demonstrate.

This plan adds an **optional, additive** field to SSE `trace` events and documents it in a new ADR
amendment (or ADR `0011` — pick in M0). Facts and honesty rules unchanged — display metadata only.

## Target state

SSE `trace` event payload becomes:

```json
{ "type": "countries_facts", "status": "USED", "agenticTurnIndex": 1 }
```

Rules:

| Rule | Detail |
| --- | --- |
| Field name | **`agenticTurnIndex`** (1-based integer) — **harness loop iteration**, not per trace event |
| Semantics (locked) | Index = 1-based agentic harness iteration; **all trace events in the same iteration share the same index** (e.g. `weather_observation` and `model_synthesis` both at index 2) |
| When present | Agentic orchestration path only, when turn index ≥ 1 |
| When omitted | Deterministic legacy paths and single-turn consultations — flat timeline unchanged |
| `final` event | Unchanged `ChatResponse`; turn index not duplicated on source cards unless spec amended later |
| Consumers | `chat-ui` groups timeline steps by turn; Java e2e ignores unknown JSON fields |

Example multi-turn stream (abbreviated):

```text
event:trace
data:{"type":"countries_facts","status":"USED","agenticTurnIndex":1}

event:trace
data:{"type":"weather_observation","status":"USED","agenticTurnIndex":2}

event:trace
data:{"type":"model_synthesis","status":"USED","agenticTurnIndex":2}

event:final
data:{ ... }
```

Chat Interface renders turn headers (“Consultation 1”, “Consultation 2”) — copy locked in M2.

## Design (locked decisions)

### 1. Documentation-first — ADR before code

Accept **`docs/adr/0011-agentic-turn-index-in-sse-trace.md`** (prefer standalone ADR for additive
protocol — amend `0009` with cross-link only). ADR must lock: “`agenticTurnIndex` = 1-based harness
iteration; all trace events in same iteration share index.” Same change set updates:

| Document | Change |
| --- | --- |
| `docs/spec/14-assistant-api-contract.md` | `trace` payload optional `agenticTurnIndex` |
| ADR `0009` | Cross-link to `0011`; note additive field |
| `improve-agentic-tool-orchestration.md` | Replace “UI events use sink only” with turn index emission |

### 2. Application boundary

- Extend `AssistantResponseSink.recordSourceOutcome` **or** add overload with turn index — domain
  enum/source types unchanged.
- `AgenticAnswerUseCase` passes harness turn index when recording consultation outcomes.
- Deterministic / pre-agentic paths omit index (mapper sends two-field JSON).

No Spring/reactor types on the port.

### 3. Chat Interface

- `TraceStep` type adds optional `agenticTurnIndex?: number`.
- `traceDisplay.ts` groups `ol` items under subheadings when index present; flat list when absent.
- **Single-index stream:** no redundant group header when only one distinct index (collapse rule).
- Display label: **“Consultation {n}”** — not “turn memory” or “agent step memory”.
- vitest: grouped render snapshot + backward compat (missing index → flat).

### 4. E2E tolerance

- `AssistantApiClient` / SSE parser: ignore unknown fields (already should via Jackson / manual parse).
- `RequiredDemoQuestionsIT` unchanged assertions on `final` — no turn index required on demo paths
  until agentic demo re-capture.

### 5. Honesty

- Turn index is **ordering metadata** for the Source-Usage Trace only.
- Does not imply the assistant “remembers” prior requests (ADR `0006`).
- Status and `type` remain authoritative for source outcome; index never overrides `final`.

## Scope

- ADR `0011` + spec 14 contract update.
- Sink + HTTP mapper + agentic use case emission of `agenticTurnIndex`.
- `chat-ui` types, parser, grouped trace timeline + CSS.
- Unit/integration tests: agentic stub emits indexed traces; deterministic stream omits field.
- Demo re-capture note if agentic changes trace shape for demo questions (optional in M3).
- `docs/ai/agentic-orchestration.md` — trace wire format paragraph.

## Out of scope / non-goals

- New SSE event type (`agentic_turn` separate from `trace`) — YAGNI.
- Turn index on `token` events.
- Turn index in `final.sources[]` — **non-goal for v1**; reviewer note: multi-source agentic demo
  (#3) may show flat source cards while trace is grouped; card grouping is a follow-up if needed.
- Tool-call argument payloads or reasoning text on the wire (security + scope).
- Chat Interface session display changes (`improve-chat-interface.md`).
- Retroactive turn index for pre-agentic deterministic routes (omit field).

## Invariants (must hold)

- **Additive** JSON — old clients ignoring `agenticTurnIndex` still work.
- Terminal `final` remains authoritative for answer text and source cards.
- Field present → integer ≥ 1; never `0` or negative.
- Vocabulary: **Source-Usage Trace**, **Knowledge Source**, **agentic turn** (logging) vs
  **Consultation n** (UI label).

## Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Protocol drift | ADR + spec 14 before implementation |
| UI clutter | Collapse single-step turns; group headers only when index changes |
| Deterministic regression | Omit field on non-agentic paths; e2e unchanged |
| Jackson strict parsing | E2e client test with extra field |

## Definition of Done

- [ ] M0: ADR `0011` Accepted; spec 14 lists optional `agenticTurnIndex` with locked iteration semantics.
- [ ] Agentic path emits index on each `trace` event; multiple traces per index allowed; deterministic
  path omits field (integration test: JSON has exactly `type` + `status`).
- [ ] Backend stub: 2-iteration agentic stream → indices monotonic; multiple traces share same index
  within iteration.
- [ ] `chat-ui` groups trace when index present; flat when absent; single-index → no redundant header
  (vitest).
- [ ] Backward compat: parse legacy two-field trace in ui tests.
- [ ] `./mvnw -pl assistant-app test` — paste trace emission test output.
- [ ] `cd chat-ui && npm run test` — paste trace display tests.
- [ ] Deterministic demo e2e unchanged; **agentic demo re-capture required** when agentic is default
  path (mandatory in M3); omitted for deterministic-only paths.
- [ ] `docs/ai/agentic-orchestration.md` updated.

## Milestones

### M0 — ADR gate (stop if red)

1. Draft ADR `0011` with field name, **locked iteration semantics** (shared index per harness
   iteration), omission rules, backward compatibility.
2. Update spec 14 `trace` table.
3. Critic review: confirm no ADR `0006` violation (metadata only).

**Verify:** ADR status Accepted in docs. **Stop if spec conflicts with ADR `0009` terminal
authority — resolve before code.**

### M1 — Backend emission (gate: agentic M3+ landed)

- Extend sink contract + `SseAssistantResponseSink` / mapper — **coordinate with agentic M5** (single
  change set for sink overload + mapper; no duplicate emission work).
- `AgenticAnswerUseCase` passes harness turn index when recording consultation outcomes.
- Integration test: stubbed multi-turn agentic → parse SSE, assert indices monotonic; multiple traces
  per index allowed.

**Verify:** paste integration test output. **Stop if `AgenticAnswerUseCase` absent — blocked.**

### M2 — Chat Interface grouping

- Types + `sseParser` accept optional field.
- `traceDisplay.ts` consultation group headers + CSS; collapse when single distinct index.
- vitest snapshots for grouped, flat, and single-index timelines.

**Verify:** paste vitest output; manual light/dark spot-check. **May start with mocked payloads
before agentic M3 lands.**

### M3 — E2E tolerance + docs

- Confirm Java SSE client ignores/exposes optional field without failure.
- **Deterministic e2e unchanged.** Re-capture agentic demo JSON when agentic is default path
  (mandatory); skip re-capture for deterministic-only paths.
- Agentic orchestration AI doc + README one-liner.

**Verify:** e2e profile green or honest skip; mark plan **landed — `<sha>`**.

## Documentation impact

- `docs/adr/0011-agentic-turn-index-in-sse-trace.md` — new Accepted ADR.
- `docs/spec/14-assistant-api-contract.md` — trace payload column.
- `docs/adr/0009-stream-assistant-api-over-sse.md` — cross-link.
- `docs/ai/agentic-orchestration.md` — wire format + UI grouping note.
- `improve-chat-interface.md` follow-up link.

## Coordination

| Dependency | Milestone | Rule |
| --- | --- | --- |
| Agentic M3+ loop live | M1 backend emission | **Hard gate** — plan BLOCKED until `AgenticAnswerUseCase` exists |
| Agentic M5 SSE trace | M1 sink + mapper | **Single change set** — agentic M5 links here; no duplicate emission |
| Chat Interface M2a trace UI | M2 grouping | May start with mocked payloads in parallel |
| [`backend-sse-disconnect-cancellation.md`](backend-sse-disconnect-cancellation.md) | After M1 | Serial after this plan M1 |
| Phase 8 submission | M3 e2e | Deterministic e2e unchanged; agentic re-capture when agentic default |

## Round-1 critic resolutions (authoritative; supersede the body where in conflict)

- **[T4R1-1]** Status: **BLOCKED on agentic M3+** for M1; M0 ADR + M2 UI may proceed earlier.
- **[T4R1-2]** Index semantics locked: 1-based **harness iteration**; all traces in same iteration
  share index (ADR AC).
- **[T4R1-3]** Agentic M5 must link here; single change set for sink overload + mapper.
- **[T4R1-4]** `final.sources[]` without index — non-goal v1; reviewer note for flat cards vs grouped trace.
- **[T4R1-5]** Demo re-capture: mandatory for agentic default path; deterministic e2e unchanged.
- **[T4R1-6]** UI prerequisite scoped to **M2a** (trace timeline exists); M3 polish not required.
- **[T4R1-7]** Backend AC: multiple traces per index allowed; deterministic JSON exactly two fields.
- **[T4R1-8]** UI collapse rule: single distinct index → no redundant group header.
