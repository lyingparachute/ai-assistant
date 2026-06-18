# ExecPlan — bounded agentic tool-calling orchestration

Status: **Proposed — implementation-ready (2026-06-18)**

Owner: TBD

**Restoration note:** This plan lived at `docs/plans/improve-agentic-tool-orchestration.md` until
commit `8928ad2` removed internal working plans from the submission surface. It is restored here
for implementation with package/port names reconciled to the capability-sliced layout (`4a253f6`,
`8d808bf`).

Source: `AGENTS.md` §8 bounded tool-loop sentence; `docs/spec/05-architecture.md` §9;
`SourceRoutingPolicy` off-demo → `Unsupported`; ADR `0009` SSE streaming.

Scope module: `assistant-app` — `answering` facade and agentic loop, `synthesis` tool-calling LLM
port and Ollama adapter. `chat-ui` unchanged unless trace copy needs a label tweak (defer).

## Prerequisites (met)

- [`backend-hygiene`](../../implementation-notes.md) **landed** — Boot `3.5.15`, dependency hygiene
  on touched adapter code.
- ADR [`0009`](../adr/0009-stream-assistant-api-over-sse.md) **Accepted** — `POST /api/chat` is SSE;
  `AnswerQuestion.execute(Command)` with `AssistantResponseSink`; `LlmPort.generate(PromptContext,
  TokenSink)`; `AssistantChatProperties` + bounded `chatStreamExecutor` + cancellation on worker
  thread. Agentic work builds on this sink and executor — no parallel HTTP model.
- **Capability-sliced packages landed** — `answering`, `countryfacts`, `weather`, `rag`, `synthesis`,
  `shared` under `dev.localassistant.assistant.*`. No package-restructure follow-up remains in this
  plan.
- Demo path green after streaming (`docs/demo/final-answers.md`, `./mvnw verify -P e2e`).

## Required gates

- **M0 spike green — met 2026-06-18.** `qwen3:4b` + Spring AI `1.1.8` completed one
  user-controlled tool-call round-trip (propose → execute → structured follow-up) before loop code.
  Evidence: `implementation-notes.md`, section "M0 agentic tool-calling spike".
- Spring AI tool-calling API re-verified against the **post-hygiene** pinned version in M0.
- ADR [`0010`](../adr/0010-bounded-agentic-tool-orchestration.md) **Accepted** (documentation-first;
  status moves from Proposed to Accepted in the same change that starts M1 implementation) — not met.

## Why now

Today the assistant answers only demo-shaped questions. `SourceRoutingPolicy` keyword-matches Germany
and Munich literally; "capital of France" or "weather in Tokyo" fall through to `Unsupported`
(verified: `answering/domain/SourceRoutingPolicy.java`). That is correct for the recruitment demo,
but it is the assistant's most visible ceiling.

`AGENTS.md` §8 already specifies the future harness: conservative max-turn limit, timeout,
cancellation, typed `{ ok, error, hint }` tool results. This plan implements that sentence as an
**opt-in** capability behind `assistant.orchestration.mode`. The deterministic path stays default so
demo reproducibility and `RequiredDemoQuestionsIT` stay stable.

## Target state

A new **`OrchestrateQuestionUseCase` facade** in `answering` is the sole inbound application entry
for chat (`ChatController` stops depending on `AnswerQuestionUseCase` directly). It implements the
existing `AnswerQuestion` inbound port and selects:

| `assistant.orchestration.mode` | Behaviour |
| --- | --- |
| `deterministic` (default) | Today — delegate to `AnswerQuestionUseCase` unchanged |
| `agentic-fallback` | Deterministic when `SourceRoutingPolicy` matches; **only** `Unsupported` → agentic loop |
| `agentic` | Agentic loop for non-demo-shaped questions; **demo-shaped routes still deterministic** (policy match wins first) |

The agentic path runs a **bounded function-calling loop** over three LLM-facing capabilities mapped
to existing inbound ports. Each capability returns a model-facing DTO derived from its sealed outcome:
`ToolExecutionResult` for country/weather and `RagRetrievalResult` for RAG knowledge. The harness
records Knowledge Source consultations; **`ResponseComposer`** assembles the same four `AnswerSource`
types as today. The model never supplies provenance.

Concretely: ask "What is the capital of France, and the weather there?" in `agentic-fallback`
→ `country_lookup(France)` → Paris → `get-weather(Paris)` → grounded answer with
`countries_facts` + `weather_observation` both `USED`. Unknown country → typed tool error or
source-unavailable outcome with hint → loop stops → no fabricated fact.

With streaming landed, agentic routes emit **Source-Usage Trace** events via
`sink.recordSourceOutcome` as each Knowledge Source resolves; final synthesis tokens stream through
`LlmPort.generate(..., TokenSink)`; terminal `final` remains authoritative.

## Locked architectural decisions

1. **Documentation-first.** ADR `0010` Accepted + `docs/spec/05-architecture.md` §9 note **before**
   production loop code. Required demo questions stay policy-routed in all modes. M0 may run as a
   throwaway spike before ADR acceptance because it produces evidence for the decision and must not add
   production loop code.

2. **Facade — `OrchestrateQuestionUseCase`.** Single constructor-injected bean implementing
   `AnswerQuestion`:

   ```text
   ConversationTurn execute(AnswerQuestion.Command command)
   ```

   - `command` carries `UserQuestion` + `AssistantResponseSink`.
   - Does **not** create an `AssistantRequestTrace` when delegating to the deterministic
     `AnswerQuestionUseCase`; that use case already owns deterministic trace lifecycle.
   - Calls `sourceRoutingPolicy.route(question)`.
   - If `mode` is `deterministic` **or** route is not `Unsupported` **or** question matches a
     demo-shaped policy arm → delegate to `AnswerQuestionUseCase.execute(command)` unchanged.
   - Else → create agentic `AssistantRequestTrace` and call `AgenticAnswerUseCase.execute(command,
     trace)`.
   - `ChatController` injects only `AnswerQuestion` (the facade bean replaces the use-case bean).

3. **Separate ports — do not extend `LlmPort`.**
   - **`LlmToolCallPort`** (`synthesis/domain/port/outbound/`) — multi-turn propose: input =
     conversation + tool results; output = sealed `ToolCallStep` (`ProposeCalls`,
     `FinalStructuredAnswer`, `SourceUnavailable`). Implemented in `synthesis/infrastructure/`
     (e.g. `OllamaToolCallAdapter`).
   - **`LlmPort`** — unchanged role: single-shot synthesis with `TokenSink` (ADR `0009`). Agentic
     loop calls `LlmPort` only for **final** grounded synthesis after tools succeed.

4. **Inbound port mapping (application harness → capabilities).**

   | LLM-facing capability | Inbound port | Package |
   | --- | --- | --- |
   | `country_lookup` | `ResolveCountryFacts` | `countryfacts/domain/port/inbound/` |
   | `get-weather` | `ResolveWeatherObservation` | `weather/domain/port/inbound/` |
   | `product_knowledge_search` | `RetrieveRagKnowledge` | `rag/domain/port/inbound/` |

   MCP runtime tool names `country_lookup` and `get-weather` match `application.yml`
   (`assistant.mcp.countries.tool-name`, `assistant.mcp.weather.tool-name`). RAG is a **Knowledge
   Source**, not an MCP tool.

5. **Provenance via `ResponseComposer` only.** The harness records `RecordedConsultation` entries
   (source type, port outcome variant, grounded payload refs). It does **not** trust model JSON for
   `AnswerSource`. New package-private composer entry:

   ```text
   AssistantAnswer composeAgentic(
       List<RecordedConsultation> consultations,
       LlmResult synthesisResult,
       String traceCorrelationId)
   ```

   Maps consultations → `countries_facts | weather_observation | rag_knowledge` cards; synthesis
   → `model_synthesis` only when `LlmResult.Success` and grounding exists.

6. **Model-facing tool DTO ≠ application outcome types.** Infrastructure `ModelToolResultPresenter`
   (`synthesis/infrastructure/` or `answering/infrastructure/`) maps sealed variants to JSON the model
   sees:

   | Application outcome | Model DTO |
   | --- | --- |
   | `ToolExecutionResult.Success<T>` | `{ "ok": true, "data": {…} }` |
   | `ToolExecutionResult.ToolError<T>` | `{ "ok": false, "error": "…", "hint": "…" }` |
   | `ToolExecutionResult.SourceUnavailable<T>` | `{ "ok": false, "error": "…", "hint": "…" }` |
   | `RagRetrievalResult.Success` | `{ "ok": true, "data": { "snippets": […] } }` |
   | `RagRetrievalResult.NoRelevantKnowledge` | `{ "ok": false, "error": "insufficient product knowledge", "hint": "answer that the product knowledge source has no relevant snippets" }` |
   | `RagRetrievalResult.SourceUnavailable` | `{ "ok": false, "error": "…", "hint": "…" }` |

   Domain/application never serializes application outcomes for the model directly.

7. **Bounded loop (`AgenticAnswerUseCase` in `answering/domain/`).** Application-owned harness:
   - Hard cap **`max-turns`** default **8** (`@Positive` on config record).
   - Whole-request **`request-deadline-seconds`** distinct from `assistant.llm.timeout-seconds` and
     MCP `timeout-seconds`.
   - Per-tool execution reuses existing port timeouts.
   - Cancellation: check worker-thread interrupt / streaming cancellation handle between turns
     (inherits `chatStreamExecutor` from ADR `0009`).
   - On cap/deadline/cancel without grounded answer → `ResponseComposer.composeAgenticIncomplete`
     (typed refusal, not a guess).

8. **Config — `AssistantOrchestrationProperties` (`assistant.orchestration.*`)** in
   `answering/infrastructure/config/`:

   | Property | Default | Purpose |
   | --- | --- | --- |
   | `mode` | `deterministic` | `deterministic \| agentic \| agentic-fallback` |
   | `max-turns` | `8` | Loop iteration cap |
   | `request-deadline-seconds` | `120` | Whole agentic request budget |

   `@ConfigurationProperties` record + binding test. No scattered `@Value`.

9. **Reasoning-first structured step schema.** `LlmToolCallPort` prompts require a short
   `reasoning` field before tool proposals or final structured answer. System prompt forbids
   country/weather/product facts from model memory. RAG stays grounding-only: empty retrieval →
   insufficient product knowledge path, unchanged.

10. **Tracing — minimal in this plan.** `AssistantRequestTrace` gains agentic turn markers (turn
    index, capability name, ok/error) for logs. UI-facing events use `sink.recordSourceOutcome` —
    same four `SourceType` values as today. **Optional SSE `agenticTurnIndex` on trace events** is
    deferred to a separate future plan, `sse-trace-agentic-turn-index` (blocked until this plan M3+).

11. **Irreversible-action gating (forward-looking).** All current capabilities are read-only. Future
    state-changing tools require explicit harness gating — documented in ADR `0010`.

12. **Demo + e2e stability.** `mode=deterministic` (default) → behaviour equivalent to pre-agentic
    baseline. `RequiredDemoQuestionsIT` runs with default config — no assertion changes. Agentic
    live verification uses **off-demo** questions only in paste evidence.

13. **Wiring.** Extend `AnsweringUseCaseConfiguration` (or sibling orchestration config) to register
    the facade as the `AnswerQuestion` bean when orchestration dependencies are present. Retire direct
    `AnswerQuestionUseCase` bean exposure to HTTP layer.

## Scope

- ADR `0010` + spec `05` §9 note.
- `AssistantOrchestrationProperties` + binding test.
- `LlmToolCallPort` + `OllamaToolCallAdapter` + `ToolCallStep` sealed types.
- `ModelToolDescriptor` registry (three capabilities) + `ModelToolResultPresenter`.
- `AgenticAnswerUseCase` — loop, `RecordedConsultation` collector, deadline/cancel checks.
- `ResponseComposer.composeAgentic` (+ incomplete variant).
- `OrchestrateQuestionUseCase` facade; `ChatController` / `ChatStreamTask` wiring swap.
- `AssistantRequestTrace` agentic turn logging (internal; no SSE protocol change in this plan).
- Per-route `recordSourceOutcome` emissions in agentic path.
- Tests: stubbed-LLM loop matrix (happy, tool-error/source-unavailable, max-turn, deadline, cancel,
  no-fabrication);
  facade routing tests (`deterministic` / `agentic-fallback` / demo-shaped override);
  `OllamaToolCallAdapter` contract test behind M0 assumptions; boundary grep.
- `docs/ai/` entry; README limitations update (`mode` flag, opt-in).

## Non-goals

- Conversational memory across requests (ADR `0006`).
- Parallel/multi-agent orchestration, sandboxed code execution, background jobs.
- Replacing deterministic routing as default or changing required demo question behaviour.
- New external tools/providers beyond the three existing Knowledge Sources.
- SSE `agenticTurnIndex` wire field (separate future plan `sse-trace-agentic-turn-index`).
- `chat-ui` feature work beyond optional trace label copy.
- Making agentic the default without eval note recommendation.

## Invariants

- Honesty: no country/weather/product fact from model memory; every fact traces to port consultation
  recorded in harness; failed source → source-unavailable, never fabrication.
- Boundedness: loop terminates within `max-turns`, `request-deadline-seconds`, or cancellation.
- Architecture: domain/application code never import Spring AI, Ollama, MCP SDK, HTTP, or reactor
  types (ADR `0001`, `0005`).
- Provenance: only `ResponseComposer` builds `AnswerSource` / `AssistantAnswer`.
- Backward compatibility: `mode=deterministic` → existing suite green unchanged; `/api/chat` SSE
  contract unchanged; `final` payload shape unchanged.
- Glossary: traces/docs say **Source-Usage Trace** and **Knowledge Source**, not "tool-call trace"
  or "memory".

## Risks and open questions

- **Small-model tool-calling reliability.** `qwen3:4b` may propose wrong tools/args. Mitigations: M0
  spike gate, reasoning-first schema, tight descriptions, low turn cap, default `deterministic`, eval
  note in M6. OPEN: eval set size (recommend ≥10 off-demo questions).
- **Latency.** Multiple LLM round-trips. Mitigation: turn cap, deadline, prompt-cache-friendly static
  system/tools block.
- **Honesty regressions.** Mitigation: provenance in composer from recorded consultations; explicit
  no-fabrication-on-tool-failure tests.
- **Scope creep into general agent framework.** Mitigation: three capabilities only; non-goals fence.

## Definition of Done

- [ ] ADR `0010` **Accepted**; spec `05` §9 agentic paragraph current.
- [x] M0 spike paste: `qwen3:4b` one tool-call round-trip green on post-hygiene Spring AI (evidence
      in plan commit or `implementation-notes.md`).
- [ ] `mode=deterministic` (default): `./mvnw test` green; demo questions unchanged (paste totals).
- [ ] `mode=agentic-fallback`: "capital of France" → live `country_lookup`, Paris,
      `countries_facts` USED (paste live SSE `final` + trace).
- [ ] `mode=agentic-fallback`: "weather in Tokyo" → live `get-weather`, observation,
      `weather_observation` USED (paste).
- [ ] Multi-capability off-demo question composes ≥2 Knowledge Sources in one answer (test + paste).
- [ ] Unknown country → tool error or source-unavailable with **no** fabricated fact (test + paste).
- [ ] Loop hits `max-turns` → typed incomplete answer, not guess (deterministic test).
- [ ] Request exceeds `request-deadline-seconds` → typed incomplete answer (deterministic test).
- [ ] Cancellation between turns → stream ends without hang (test; uses `chatStreamExecutor`).
- [ ] Demo-shaped questions (`Germany`, `Munich`, CDQ, etc.) stay deterministic even when
      `mode=agentic` (facade unit test).
- [ ] Boundary grep: no Spring AI / Ollama / HTTP in `answering`, `countryfacts`, `weather`, `rag`,
      `synthesis` **domain** packages (pattern from ADR `0009` verification).
- [ ] `RequiredDemoQuestionsIT` green with default config (paste `./mvnw verify -P e2e`).
- [ ] Eval note `docs/demo/agentic-tool-selection-eval.md` (or section in implementation-notes):
      `qwen3:4b` accuracy on small set + default-mode recommendation.
- [ ] README documents `assistant.orchestration.mode`; limitations state opt-in agentic.

## Milestones

### M0 — Ollama tool-call spike (**required gate**)

Minimal standalone test or scratch adapter: `country_lookup` propose → execute → model receives
structured result. **Stop if red** — do not start M1 loop code. Paste output in plan commit or
`implementation-notes.md`.

Use the user-controlled Spring AI path (`ChatModel` + tool callbacks + `DefaultToolCallingManager` or
current equivalent) rather than the automatic `ChatClient` advisor path, because production needs
explicit max-turn, deadline, and consultation recording.

**Gate criteria:** one full round-trip with real `qwen3:4b` (or documented skip with reason); Spring
AI `1.1.8` tool-calling API verified against the pinned version.

**Result:** green on 2026-06-18. The model proposed `country_lookup`, Spring AI
`DefaultToolCallingManager` executed it exactly once, and the follow-up answer used the structured
tool result (`Paris`). No production loop code was added.

### M1 — ADR `0010` Accepted + `AssistantOrchestrationProperties`

Spec §9 note; binding test; default `mode=deterministic` verified in config test.

**Gate criteria:** ADR status Accepted; `./mvnw -pl assistant-app test -Dtest=AssistantOrchestrationPropertiesTest` green.

### M2 — `LlmToolCallPort` + `OllamaToolCallAdapter` + descriptors + presenter

Contract test with stubbed `ChatModel` tool response; three capability schemas locked to runtime
names.

**Gate criteria:** contract test paste; boundary grep clean on new adapter.

### M3 — `AgenticAnswerUseCase` loop

Stubbed `LlmToolCallPort` tests: happy, tool-error/source-unavailable, max-turn, deadline, cancel,
no-fabrication;
`RecordedConsultation` collector.

**Gate criteria:** loop matrix tests green (paste class name + totals). Unblocks
future `sse-trace-agentic-turn-index` M1 backend work.

### M4 — `ResponseComposer.composeAgentic` + `OrchestrateQuestionUseCase` facade + HTTP wiring

Facade routing matrix tests; `ChatController` / `ChatStreamTask` use facade `AnswerQuestion` bean only.

**Gate criteria:** facade tests green; `ChatControllerStreamingTest` updated and green.

### M5 — Sink/trace integration + live verification

Agentic SSE `recordSourceOutcome` emissions; off-demo live pastes; `./mvnw test` green (paste totals).
Coordinate with `sse-trace-agentic-turn-index` if that future plan starts in parallel.

**Gate criteria:** live SSE pastes for two off-demo scenarios; full module tests green.

### M6 — Eval note + README + `docs/ai/`

`agentic-tool-selection-eval.md`; limitations; mark plan **landed — `<sha>`**.

**Gate criteria:** eval note committed; README limitations section updated.

## Documentation impact

- **New:** `docs/adr/0010-bounded-agentic-tool-orchestration.md`.
- `docs/spec/05-architecture.md` §9 — agentic mode paragraph + link ADR `0010`.
- `README.md` — `assistant.orchestration.mode`; limitations (opt-in; demo stays deterministic).
- `docs/ai/agentic-orchestration.md` — material AI-assisted implementation entry.

## Follow-ups (separate plans)

- `sse-trace-agentic-turn-index` — optional `agenticTurnIndex` on SSE `trace` events; **blocked until
  M3+**; depends on this plan.
- Agentic as default (only after eval note + explicit ADR amendment).
- Boot 4 / Spring AI 2 platform migration.
