# ExecPlan — bounded agentic tool-calling orchestration

Status: proposed — PLAN-READY pending approval (critic round folded in)
Owner: TBD
Source: `CLAUDE.md` §8 bounded tool-loop sentence; `docs/spec/05-architecture.md:295`;
`SourceRoutingPolicy.java:9-11` (off-demo → `Unsupported`); AI_DEVS Agent Harness research.
Scope module: `assistant-app` (orchestration facade, agentic loop, outbound tool-calling LLM port).
`chat-ui` unchanged unless trace timeline needs a label tweak for agentic routes (defer).

## Prerequisites

- [`backend-hygiene.md`](backend-hygiene.md) **landed** — Boot `3.5.12`, Commons Lang, Lombok on
  touched orchestration/adapter code.
- [`stream-chat-answers-and-source-usage-trace.md`](stream-chat-answers-and-source-usage-trace.md)
  **landed** — ADR `0009` Accepted; `POST /api/chat` is SSE; `AnswerQuestionUseCase.answer(
  UserQuestion, AssistantResponseSink)`; `LlmPort.generate(PromptContext, TokenSink)`;
  `AssistantChatProperties` + bounded executor + cancellation on worker thread. Agentic work
  builds on this sink and executor — no parallel HTTP model.
- Demo path still green after streaming (`docs/demo/final-answers.md`, `./mvnw verify -P e2e`).
- **M0 spike green** (see Milestones) — `qwen3:4b` + post-hygiene Spring AI `1.1.x` completes at
  least one tool-call round-trip (propose → execute → structured follow-up) before loop code.
- ADR `docs/adr/0010-bounded-agentic-tool-orchestration.md` **Accepted** (documentation-first;
  status moves from Proposed to Accepted in the same change that starts M1 implementation).
- Re-verify Spring AI tool-calling API against the **post-hygiene** pinned version in M0.

## Why now

Today the assistant answers only demo-shaped questions. `SourceRoutingPolicy` keyword-matches
Germany and Munich literally; "capital of France" or "weather in Tokyo" fall through to
`Unsupported` (verified: `SourceRoutingPolicy.java:9-11,41`). That is correct for the
recruitment demo, but it is the assistant's most visible ceiling.

`CLAUDE.md` §8 already specifies the future harness: conservative max-turn limit, timeout,
cancellation, typed `{ ok, error, hint }` tool results. This plan implements that sentence as an
**opt-in** capability behind a config flag. The deterministic path stays default so demo
reproducibility and `RequiredDemoQuestionsIT` stay stable.

## Target state

A new **`OrchestrateQuestionUseCase` facade** is the sole inbound application entry point
(`ChatController` stops calling `AnswerQuestionUseCase` directly). It selects:

| `assistant.orchestration.mode` | Behaviour |
| --- | --- |
| `deterministic` (default) | Today — `AnswerQuestionUseCase` only |
| `agentic-fallback` | Deterministic when `SourceRoutingPolicy` matches; **only** `Unsupported` → agentic loop |
| `agentic` | Agentic loop for non-demo-shaped questions; **demo-shaped routes still deterministic** (policy match wins first) |

The agentic path runs a **bounded function-calling loop** over three LLM-facing capabilities
(mapped to existing ports). Every capability returns a model-facing DTO derived from sealed
`ToolExecutionResult`; the harness records Knowledge Source consultations; **`ResponseComposer`**
assembles the same four `AnswerSource` types as today. The model never supplies provenance.

Concretely: ask "What is the capital of France, and the weather there?" in `agentic-fallback`
→ `country_lookup(France)` → Paris → `get-weather(Paris)` → grounded answer with
`countries_facts` + `weather_observation` both `USED`. Unknown country → typed not-found with
hint → loop stops → source-unavailable, never a fabricated fact.

With streaming landed, agentic routes emit **Source-Usage Trace** events via
`AssistantResponseSink.recordSourceOutcome` as each Knowledge Source resolves; final synthesis
tokens stream through `LlmPort.generate(..., TokenSink)`; terminal `final` remains authoritative.

## Design (locked decisions)

1. **Documentation-first.** ADR `0010` Accepted + `docs/spec/05-architecture.md` §9 note
   **before** production loop code. ADR scopes the deviation from deterministic routing to
   opt-in modes only; required demo questions stay policy-routed in all modes.

2. **Facade wiring — `OrchestrateQuestionUseCase`.** Single constructor-injected bean:

   ```text
   ConversationTurn answer(UserQuestion question, AssistantResponseSink sink)
   ```

   - Always starts `AssistantRequestTrace`.
   - Calls `sourceRoutingPolicy.route(question)`.
   - If mode is `deterministic` **or** route is not `Unsupported` **or** question matches a
     demo-shaped policy arm → delegate to existing `AnswerQuestionUseCase.answer(question, sink)`
     unchanged.
   - Else → `AgenticAnswerUseCase.answer(question, sink, trace)`.
   - `ChatController` injects only the facade.

3. **Separate ports — do not extend `LlmPort`.**
   - **`LlmToolCallPort`** — multi-turn propose: input = conversation + tool results; output =
     sealed `ToolCallStep` (`ProposeCalls`, `FinalStructuredAnswer`, `SourceUnavailable`).
     Lives in `llm` package; implemented in `adapters/outbound/ollama`.
   - **`LlmPort`** — unchanged role: single-shot synthesis with `TokenSink` (ADR `0009`).
     Agentic loop calls `LlmPort` only for the **final** grounded synthesis after tools succeed.

4. **Provenance via `ResponseComposer` only.** The harness records `RecordedConsultation`
   entries (source type, port outcome variant, grounded payload refs). It does **not** trust
   model JSON for `AnswerSource`. New package-private composer entry:

   ```text
   AssistantAnswer composeAgentic(
       List<RecordedConsultation> consultations,
       LlmResult synthesisResult,
       String traceCorrelationId)
   ```

   Maps consultations → `countries_facts | weather_observation | rag_knowledge` cards; synthesis
   → `model_synthesis` only when `LlmResult.Success` and grounding exists. Same honesty rules
   as deterministic routes.

5. **Model-facing tool DTO ≠ `ToolExecutionResult`.** Adapter-layer `ModelToolResultPresenter`
   maps sealed variants to JSON the model sees:

   | `ToolExecutionResult` | Model DTO |
   | --- | --- |
   | `Success<T>` | `{ "ok": true, "data": {…} }` |
   | `ToolError<T>` | `{ "ok": false, "error": "…", "hint": "…" }` |
   | `SourceUnavailable<T>` | `{ "ok": false, "error": "…", "hint": "…" }` |

   Domain/application never serializes tool results for the model directly.

6. **LLM-facing capability names (locked to runtime).**

   | Capability name | Backing | Notes |
   | --- | --- | --- |
   | `country_lookup` | `CountriesPort` | Matches `application.yml` `tool-name` |
   | `get-weather` | `WeatherPort` | Matches `application.yml` `tool-name` — **not** `weather_current` |
   | `product_knowledge_search` | `RagKnowledgePort` | **Knowledge Source**, not an MCP tool |

   Internal docs/traces use **Knowledge Source** / **Source-Usage Trace** vocabulary
   (`CONTEXT.md`). Do not call trace events "tool-call trace".

7. **Bounded loop (`AgenticAnswerUseCase`).** Application-owned harness:
   - Hard cap **`max-turns`** default **8** (`@Positive` on config record).
   - Whole-request **`request-deadline-seconds`** distinct from `assistant.llm.timeout-seconds`
     and MCP `timeout-seconds`.
   - Per-tool execution reuses existing port timeouts.
   - Cancellation: check worker-thread interrupt / streaming cancellation handle between turns
     (inherits streaming executor from ADR `0009`).
   - On cap/deadline/cancel without grounded answer → `ResponseComposer.composeAgenticIncomplete`
     (typed refusal, not a guess).

8. **Config record — `AssistantOrchestrationProperties` (`assistant.orchestration.*`).**

   | Property | Default | Purpose |
   | --- | --- | --- |
   | `mode` | `deterministic` | `deterministic \| agentic \| agentic-fallback` |
   | `max-turns` | `8` | Loop iteration cap |
   | `request-deadline-seconds` | `120` | Whole agentic request budget |

   `@ConfigurationProperties` record + binding test (refactor-3b pattern). No scattered
   `@Value`.

9. **Reasoning-first structured step schema.** `LlmToolCallPort` prompts require a short
   `reasoning` field before tool proposals or final structured answer. System prompt forbids
   country/weather/product facts from model memory. RAG stays grounding-only: empty retrieval
   → insufficient product knowledge path, unchanged.

10. **Tracing extension.** `AssistantRequestTrace` gains agentic turn markers (turn index,
    capability name, ok/error) for logs and optional debug. **UI-facing** events use
    `sink.recordSourceOutcome(SourceType, status)` — same four types as streaming plan. Internal
    `Port` names never appear in SSE.

11. **Irreversible-action gating (forward-looking).** All current capabilities are read-only.
    Future state-changing tools require explicit harness gating — documented in ADR `0010`.

12. **Demo + e2e stability.** `mode=deterministic` (default) → byte-for-byte behaviour vs
    pre-agentic baseline. `RequiredDemoQuestionsIT` runs with default config — no assertion
    changes. Agentic live verification uses **off-demo** questions only in paste evidence.

## Scope

- ADR `0010` + spec `05` §9 note.
- `AssistantOrchestrationProperties` + binding test.
- `LlmToolCallPort` + `OllamaToolCallAdapter` + `ToolCallStep` sealed types.
- `ModelToolDescriptor` registry (three capabilities) + `ModelToolResultPresenter`.
- `AgenticAnswerUseCase` — loop, `RecordedConsultation` collector, deadline/cancel checks.
- `ResponseComposer.composeAgentic` (+ incomplete variant).
- `OrchestrateQuestionUseCase` facade; `ChatController` wiring swap.
- `AssistantRequestTrace` agentic turn logging.
- Per-route `recordSourceOutcome` emissions in agentic path (streaming prerequisite).
- Tests: stubbed-LLM loop matrix (happy, not-found, max-turn, deadline, cancel, no-fabrication);
  facade routing tests (`deterministic` / `agentic-fallback` / demo-shaped override);
  `OllamaToolCallAdapter` contract test behind M0 assumptions; boundary grep.
- `docs/ai/` entry; README limitations update (`mode` flag, opt-in).

## Out of scope / non-goals

- Conversational memory across requests (ADR `0006`).
- Parallel/multi-agent orchestration, sandboxed code execution, background jobs.
- Replacing deterministic routing as default or changing required demo question behaviour.
- New external tools/providers beyond the three existing Knowledge Sources.
- `chat-ui` feature work beyond optional trace label copy.
- Making agentic the default without eval note recommendation.

## Invariants (must hold)

- Honesty: no country/weather/product fact from model memory; every fact traces to port
  consultation recorded in harness; failed source → source-unavailable, never fabrication.
- Boundedness: loop terminates within `max-turns`, `request-deadline-seconds`, or cancellation.
- Architecture: domain/application/orchestration (except outbound port interfaces) never import
  Spring AI, Ollama, MCP SDK, HTTP, or reactor types.
- Provenance: only `ResponseComposer` builds `AnswerSource` / `AssistantAnswer`.
- Backward compatibility: `mode=deterministic` → existing suite green unchanged; `/api/chat`
  SSE contract unchanged; `final` payload shape unchanged.
- Glossary: traces/docs say **Source-Usage Trace** and **Knowledge Source**, not "tool-call trace"
  or "memory".

## Risks and open questions

- **Small-model tool-calling reliability.** `qwen3:4b` may propose wrong tools/args. Mitigations:
  M0 spike gate, reasoning-first schema, tight descriptions, low turn cap, default
  `deterministic`, eval note in M6. OPEN: eval set size (recommend ≥10 off-demo questions).
- **Latency.** Multiple LLM round-trips. Mitigation: turn cap, deadline, prompt-cache-friendly
  static system/tools block.
- **Honesty regressions.** Mitigation: provenance in composer from recorded consultations;
  explicit "no fabrication on tool failure" tests.
- **Scope creep into general agent framework.** Mitigation: three capabilities only; non-goals fence.

## Definition of Done

- [ ] ADR `0010` **Accepted**; spec `05` §9 note current.
- [ ] M0 spike paste: `qwen3:4b` one tool-call round-trip green on post-hygiene Spring AI.
- [ ] `mode=deterministic` (default): full existing suite green; demo questions unchanged (paste
      `./mvnw test` totals).
- [ ] `mode=agentic-fallback`: "capital of France" → live `country_lookup`, Paris, `countries_facts`
      USED (paste live SSE `final` + trace).
- [ ] `mode=agentic-fallback`: "weather in Tokyo" → live `get-weather`, observation, `weather_observation`
      USED (paste).
- [ ] Multi-capability off-demo question composes ≥2 Knowledge Sources in one answer (test + paste).
- [ ] Unknown country → self-correct or source-unavailable with **no** fabricated fact (test + paste).
- [ ] Loop hits `max-turns` → typed incomplete answer, not guess (deterministic test).
- [ ] Request exceeds `request-deadline-seconds` → typed incomplete answer (deterministic test).
- [ ] Cancellation between turns → stream ends without hang (test; uses streaming executor).
- [ ] Demo-shaped questions (`Germany`, `Munich`, CDQ, etc.) stay deterministic even when
      `mode=agentic` (facade unit test).
- [ ] Boundary grep: no Spring AI / Ollama / HTTP in `orchestration` domain packages (pattern from
      streaming plan).
- [ ] `RequiredDemoQuestionsIT` green with default config (paste `./mvnw verify -P e2e`).
- [ ] Eval note `docs/demo/agentic-tool-selection-eval.md` (or section in implementation-notes):
      qwen3:4b accuracy on small set + default-mode recommendation.
- [ ] README documents `assistant.orchestration.mode`; limitations state opt-in agentic.

## Milestones

- [ ] **M0 — Ollama tool-call spike (gate).** Minimal standalone test or scratch adapter:
      `country_lookup` propose → execute → model receives result. **Stop if red** — do not start
      loop. Paste output in plan commit or `implementation-notes.md`.
- [ ] **M1 — ADR `0010` Accepted + `AssistantOrchestrationProperties`.** Spec note; binding test;
      default `mode=deterministic` verified in config test.
- [ ] **M2 — `LlmToolCallPort` + `OllamaToolCallAdapter` + descriptors + `ModelToolResultPresenter`.**
      Contract test with stubbed `ChatModel` tool response; three capability schemas locked.
- [ ] **M3 — `AgenticAnswerUseCase` loop.** Stubbed `LlmToolCallPort` tests: happy, not-found,
      max-turn, deadline, cancel, no-fabrication; `RecordedConsultation` collector.
- [ ] **M4 — `ResponseComposer.composeAgentic` + `OrchestrateQuestionUseCase` facade + controller
      wiring.** Facade routing matrix tests; `ChatController` injects facade only.
- [ ] **M5 — Sink/trace integration + live verification.** Agentic SSE trace emissions; off-demo
      live pastes; `./mvnw test` green (paste totals).
- [ ] **M6 — Eval note + README + `docs/ai/`.** `agentic-tool-selection-eval.md`; limitations;
      mark plan landed.

## Documentation impact

- **New:** `docs/adr/0010-bounded-agentic-tool-orchestration.md` — harness, opt-in modes, demo
  routing preserved, honesty/boundedness, deviation from spec `05:295`.
- `docs/spec/05-architecture.md` §9 — agentic mode paragraph + link ADR `0010`.
- `README.md` — `assistant.orchestration.mode`; limitations (opt-in; demo stays deterministic).
- `docs/ai/agentic-orchestration.md` — material AI-assisted implementation entry.

## Round-1 critic resolutions (authoritative; supersede body on conflict)

- **[A1R1-1]** Prerequisites: hygiene → streaming (`0009`) → **M0 spike** → ADR `0010` Accepted.
- **[A1R1-2]** ADR `0010` before code — not deferred to M4.
- **[A1R1-3]** `OrchestrateQuestionUseCase` facade; `ChatController` stops calling
  `AnswerQuestionUseCase` directly.
- **[A1R1-4]** Separate **`LlmToolCallPort`**; keep **`LlmPort`** for synthesis/streaming only.
- **[A1R1-5]** Provenance locked to **`ResponseComposer.composeAgentic`** from
  `RecordedConsultation` — model JSON never trusted.
- **[A1R1-6]** Weather capability = **`get-weather`** (matches `application.yml:42`).
- **[A1R1-7]** RAG = **`product_knowledge_search`** Knowledge Source, not MCP tool name.
- **[A1R1-8]** Model DTO `{ ok, error, hint }` via **`ModelToolResultPresenter`** — not raw
  `ToolExecutionResult` serialization.
- **[A1R1-9]** Trace vocabulary = **Source-Usage Trace** / Knowledge Source (`CONTEXT.md`).
- **[A1R1-10]** Demo-shaped questions **always deterministic** even in `mode=agentic`.
- **[A1R1-11]** `AssistantOrchestrationProperties` record (`mode`, `max-turns`,
  `request-deadline-seconds`).
- **[A1R1-12]** Cancellation DoD depends on streaming executor (ADR `0009` prerequisite).
- **[A1R1-13]** Source citation: off-demo behaviour documented in **`SourceRoutingPolicy.java`**, not README.

## Follow-ups (separate plans)

- Agentic as default (only after eval note + explicit ADR amendment).
- Boot 4 / Spring AI 2 platform migration.
- Package restructure (`domain`/`infrastructure` per feature).
