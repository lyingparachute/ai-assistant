# ADR 0010: Bounded agentic tool orchestration (opt-in)

## Status

Proposed — 2026-06-18.

## Context

The assistant answers required demo questions through **deterministic** `SourceRoutingPolicy` routing
in `answering/domain`. Off-demo inputs (for example "capital of France") currently fall through to
`Unsupported` rather than answering from model memory — correct for demo honesty, but a visible
product ceiling.

`docs/spec/05-architecture.md` §9 and `AGENTS.md` §8 already require that any future tool loop be
**bounded** (conservative max-turn limit, timeout, cancellation) and return typed
`{ ok, error, hint }` outcomes. ADR `0009` landed SSE streaming with `AnswerQuestion.execute(
Command)`, `AssistantResponseSink`, and `LlmPort.generate(PromptContext, TokenSink)`; agentic work
must extend that path, not introduce a parallel HTTP model.

Verified facts (source verification 2026-06-18):

- Capability-sliced packages: `answering`, `countryfacts`, `weather`, `rag`, `synthesis`, `shared`.
  Inbound ports are `ResolveCountryFacts`, `ResolveWeatherObservation`, `RetrieveRagKnowledge`; synthesis
  outbound port is `LlmPort` with `TokenSink`.
- MCP semantic tool names in `application.yml`: `country_lookup`, `get-weather`. RAG is a Knowledge
  Source behind `RetrieveRagKnowledge`, not an MCP tool.
- `ChatController` injects `AnswerQuestion`; `AnsweringUseCaseConfiguration` wires
  `AnswerQuestionUseCase` when all four backing beans exist.
- Demo routing is keyword-scoped to Germany, Munich, CDQ, and place-synthesis prefixes — unchanged
  by this ADR.

The constraint set: hexagonal boundaries (ADR `0005`), honesty (no fabricated tool or RAG results),
demo reproducibility, and backward-compatible default behaviour.

Implementation detail is specified in
[`docs/plans/improve-agentic-tool-orchestration.md`](../plans/improve-agentic-tool-orchestration.md).

## Decision

Add an **opt-in bounded agentic tool-calling loop** behind `assistant.orchestration.mode`, default
`deterministic`, without changing required demo question routing.

- **Modes.** `deterministic` (default) preserves today's behaviour. `agentic-fallback` runs the loop
  only when `SourceRoutingPolicy` returns `Unsupported`. `agentic` prefers the loop for non-demo
  questions but **demo-shaped policy matches always win** — Germany, Munich, CDQ, and related demo
  routes stay deterministic in every mode.
- **Facade.** `OrchestrateQuestionUseCase` in `answering` becomes the sole `AnswerQuestion` bean for
  HTTP; it delegates to `AnswerQuestionUseCase` or `AgenticAnswerUseCase` per mode and route.
- **Separate tool-calling port.** Introduce `LlmToolCallPort` in `synthesis/domain/port/outbound/`
  for multi-turn propose/execute cycles. Keep `LlmPort` for final grounded synthesis with `TokenSink`
  only (ADR `0009`). Do not overload `LlmPort` with tool-loop semantics.
- **Capability mapping.** Three LLM-facing capabilities, backed by existing inbound ports:

  | Capability | Port |
  | --- | --- |
  | `country_lookup` | `ResolveCountryFacts` |
  | `get-weather` | `ResolveWeatherObservation` |
  | `product_knowledge_search` | `RetrieveRagKnowledge` |

- **Bounded harness.** `AgenticAnswerUseCase` enforces `max-turns` (default 8),
  `request-deadline-seconds` (default 120), per-tool existing timeouts, and streaming cancellation
  between turns. Cap/deadline/cancel without grounding → typed incomplete answer, not a guess.
- **Honesty and provenance.** Harness records consultations; `ResponseComposer.composeAgentic` builds
  `AnswerSource` cards from recorded outcomes only — never from model JSON. Infrastructure
  `ModelToolResultPresenter` maps `ToolExecutionResult`, `RagRetrievalResult`, and
  `SourceUnavailability` to model-facing `{ ok, error, hint }` DTOs.
- **Tracing.** Agentic routes emit Source-Usage Trace via `AssistantResponseSink.recordSourceOutcome`
  as each Knowledge Source resolves. Optional SSE `agenticTurnIndex` is deferred to a future follow-up
  plan.
- **Documentation-first.** Status moves to **Accepted** in the same change that starts M1
  implementation (see ExecPlan milestones). M0 Ollama tool-call spike is a hard gate before loop
  code.

## Consequences

Benefits:

- Off-demo country, weather, and product questions can be answered honestly through real Knowledge
  Sources instead of `Unsupported`.
- Demo path and `RequiredDemoQuestionsIT` stay stable at default config.
- Tool-loop semantics stay behind application ports; Spring AI tool-calling stays in `synthesis`
  infrastructure.
- Streaming, cancellation, and terminal `final` authority from ADR `0009` are preserved.

Trade-offs:

- Additional LLM round-trips and latency on agentic routes.
- `qwen3:4b` tool-calling reliability may require eval evidence before any default-mode change.
- New facade, loop, composer paths, and config surface to test and document.
- `ChatController` and `AnswerQuestion` wiring change (facade replaces direct use-case bean).

## Alternatives Considered

- **Extend `SourceRoutingPolicy` with more keyword routes:**
  - Reason rejected: does not generalize; still guesses routing without tool grounding for arbitrary
    questions.
- **Single `LlmPort` for both tool loop and synthesis:**
  - Reason rejected: mixes concerns; complicates ADR `0009` streaming contract and testing.
- **Unbounded model tool loop:**
  - Reason rejected: violates `AGENTS.md` §8 and recruitment honesty requirements.
- **Agentic as default immediately:**
  - Reason rejected: demo reproducibility and small-model reliability need eval gate (M6).
- **Expose raw application outcome JSON to the model:**
  - Reason rejected: leaks domain shape; `{ ok, error, hint }` presenter keeps adapter boundary clear.

## Verification

- M0 spike: `qwen3:4b` completes one user-controlled `country_lookup` round-trip before M1 (paste in
  `implementation-notes.md` or plan commit).
- `mode=deterministic`: `./mvnw test` and `./mvnw verify -P e2e` green without config override.
- Agentic modes: off-demo live SSE pastes with `countries_facts` / `weather_observation` USED;
  no-fabrication tests on tool failure.
- Boundary grep: no Spring AI / Ollama / HTTP in domain packages under `answering`, `countryfacts`,
  `weather`, `rag`, `synthesis`.
- Eval note: `docs/demo/agentic-tool-selection-eval.md` (or implementation-notes section) before
  recommending default-mode change.
