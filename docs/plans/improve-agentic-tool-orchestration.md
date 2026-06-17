# ExecPlan — bounded agentic tool-calling orchestration

Status: proposed — for review (no code until approved)
Owner: TBD
Source: limitation in `README.md` ("Source routing is deterministic … scoped to the fixed
demo questions", `SourceRoutingPolicy`); honesty guardrails in `CLAUDE.md` §8; best-practice
research from the `nlm` AI_DEVS notebook (Agent Harness; bounded tool loops).
Scope module: `assistant-app` (orchestration + a new outbound LLM tool-calling capability).

## Why now

Today the assistant answers only a fixed set of demo questions. `SourceRoutingPolicy`
keyword-matches Germany and Munich literally; "capital of France" or "weather in Tokyo"
fall through to `Unsupported` (verified: `SourceRoutingPolicy.java`, comment lines 9-11).
This is correct and honest for the recruitment demo, but it is the assistant's most visible
ceiling: a reviewer who types any off-script question gets a refusal.

The deterministic policy was a deliberate, documented choice (`CLAUDE.md` §8): "The model
does not run an unbounded autonomous tool loop. Any future tool loop needs a conservative
max-turn limit, timeout, cancellation, and typed `{ ok, error, hint }` tool results." That
sentence is a spec for this plan. The goal is to let the LLM select and call the existing
tools (countries, weather, RAG) for arbitrary inputs, inside a strict, bounded harness that
preserves every honesty invariant — never trading the project's core promise (no guessing
presented as fact) for flexibility.

This is a big-scope change. It is proposed as an opt-in capability behind a feature flag, so
the deterministic path remains the default and the demo stays reproducible.

## Target state

A new `AGENTIC` route handles questions the deterministic policy does not recognise (and,
behind a flag, can become the primary path). The LLM runs a **bounded function-calling loop**
over the existing capabilities, exposed as tools with clear schemas. Every tool returns a
typed result; the model self-corrects from recovery hints; the loop cannot run away. The
final answer still carries the same `AnswerSource` provenance the deterministic path produces
(`countries_facts`, `weather_observation`, `rag_knowledge`, `model_synthesis`), so the
honesty/trace contract (`docs/spec`, `AssistantRequestTrace`) is unchanged for callers and
the UI.

Concretely: ask "What is the capital of France, and the weather there?" → the model calls
`country_lookup(France)` → gets Paris → calls `weather_current(Paris)` → composes a grounded
answer with both sources marked USED. Ask about an unknown country → a tool returns a typed
not-found with a hint → the model stops and returns a source-unavailable answer, never a
fabricated fact.

## Design (locked decisions)

Patterns below are grounded in the AI_DEVS "Agent Harness" guidance; citations are to that
notebook's research.

1. **Agent Harness, not raw SDK calls.** The loop is orchestrated in application code (a new
   `AgenticAnswerUseCase` alongside the existing `AnswerQuestionUseCase`), which owns the
   turn budget, timeouts, cancellation, and provenance assembly. The LLM only proposes tool
   calls; application code executes them and decides when to stop. Dependencies still point
   inward; the LLM tool-calling client is an outbound adapter behind a new port
   (`LlmToolCallPort` or an extension of `LlmPort`), keeping Spring AI / Ollama types out of
   the domain (ADR `0001`).

2. **Bounded loop.** Hard cap on iterations — default **8** (research range 5-15). On reaching
   the cap without a final answer, return a typed "could not complete" answer, never a guess.

3. **Timeouts + cancellation.** A per-tool timeout (reuse existing MCP `timeout-seconds`) and
   a whole-request deadline. The HTTP request is cancellable; the loop checks for cancellation
   between turns. No unbounded blocking.

4. **Typed tool results with recovery hints.** Every tool returns the project's existing
   `ToolExecutionResult` shape, surfaced to the model as `{ ok, data?, error?, hint? }`.
   Errors are semantic, not raw HTTP: e.g. unknown country → `{ ok:false, error:"country not
   found", hint:"check spelling or try the English country name" }`, so the model can
   self-correct within the budget rather than dead-ending. This mirrors the MCP tool-result
   discipline already in `.agents/skills/mcp`.

5. **Reasoning-first, honesty-preserving schema.** The model's structured output places a
   short `reasoning` field before any tool call or final answer. The final-answer schema
   forces each claim to be attributed to a tool result; the system prompt forbids answering
   country/weather/product facts from model memory. RAG stays grounding-only: if retrieval
   returns nothing relevant, the model must report insufficient product knowledge (existing
   rule, unchanged).

6. **Tool schema hygiene (signal-to-noise).** Tools are named and described for the model
   (`country_lookup`, `weather_current`, `product_knowledge_search`) with compact descriptions
   and minimal outputs — no internal hashes/IDs in what the model sees. Static system prompt
   and tool definitions (prompt-cache friendly); dynamic data (the user question) injected
   last.

7. **Deterministic path preserved.** `SourceRoutingPolicy` stays. A config flag
   (`assistant.orchestration.mode = deterministic | agentic | agentic-fallback`) selects
   behaviour; default `deterministic`. `agentic-fallback` uses the policy first and only
   invokes the loop when the policy returns `Unsupported`. The required demo questions keep
   their deterministic, reproducible path.

8. **Irreversible-action gating (forward-looking).** All current tools are read-only. The
   harness still routes any future state-changing tool through explicit gating (no
   autonomous irreversible actions), per AI_DEVS guidance — documented now so the boundary
   exists before such a tool is added.

## Scope

- New `AgenticAnswerUseCase` (orchestration): the bounded loop, turn budget, deadline,
  cancellation, provenance assembly into the existing `AssistantAnswer` / `AnswerSource`.
- New outbound port + Ollama adapter for tool-calling (function calling via Spring AI /
  Ollama), behind the architecture boundary.
- Tool descriptors mapping the existing `CountriesPort`, `WeatherPort`, `RagKnowledgePort`
  to model-facing tool schemas with recovery hints.
- A typed `mode` config (`@ConfigurationProperties`), default `deterministic`.
- Tracing: extend `AssistantRequestTrace` with per-turn tool-call events (tool, ok/error,
  turn index) and a final turn count, so the loop is observable and demo-provable.
- Tests: deterministic unit tests of the loop with a controlled (stubbed) tool-call LLM —
  multi-tool happy path, not-found self-correction, max-turn termination, timeout,
  cancellation, and honesty (no fabrication when a tool fails). Plus a controlled adapter
  contract test.

## Out of scope / non-goals

- Conversational memory across requests (ADR `0006` — still out).
- Parallel/multi-agent orchestration, sandboxed code execution, long-running background
  jobs, heartbeats (AI_DEVS mentions these; not needed here).
- Replacing the deterministic demo path as default (it stays default; agentic is opt-in).
- New external tools or providers beyond the existing three capabilities.
- Streaming responses (can be a later enhancement).

## Invariants (must hold)

- Honesty: no country/weather/product fact is ever answered from model memory; every fact
  traces to a tool result; RAG remains grounding-only. A failed/again-uncertain source →
  source-unavailable, never a fabricated value (`CLAUDE.md` §8).
- Boundedness: the loop always terminates within the turn cap and the deadline; it can be
  cancelled; it never blocks unbounded.
- Architecture: domain/application never import Spring AI / Ollama / HTTP types; the
  tool-calling LLM lives in an outbound adapter behind a port (ADR `0001`, `0005`).
- Backward compatibility: the `/api/chat` request/response contract and `AnswerSource`
  provenance shape are unchanged; the deterministic path is byte-for-byte preserved when
  `mode=deterministic`.

## Risks and open questions

- **Small-model tool-calling reliability.** `qwen3:4b` may select tools imperfectly. Mitigations:
  reasoning-first schema, tight tool descriptions, low turn cap, and `agentic-fallback` mode
  so the proven deterministic path still serves the demo. OPEN: measure qwen3:4b function-call
  accuracy on a small eval set before making agentic the default.
- **Latency/cost.** Multiple LLM round-trips per question. Mitigation: turn cap, prompt
  caching of static system/tools, deadline.
- **Honesty regressions.** A tool loop is a new surface for fabrication. Mitigation: the
  honesty invariant is enforced in application code (provenance required) and covered by
  explicit "no fabrication on tool failure" tests, not left to the prompt alone.
- **Scope creep into a general agent framework.** Mitigation: this plan builds the smallest
  harness for the three existing capabilities; the non-goals fence off the rest.

## Definition of Done

- [ ] With `mode=deterministic` (default), behaviour is identical to today (demo questions
      unchanged; full existing suite green).
- [ ] With `mode=agentic`/`agentic-fallback`, "capital of France" returns Paris via a live
      `country_lookup` tool call, and "weather in Tokyo" returns a live observation — both
      with USED provenance and a trace showing the tool calls (paste live output).
- [ ] A multi-tool question (capital + weather of an arbitrary country) composes both sources
      in one answer.
- [ ] Unknown country → the loop self-corrects or stops and returns source-unavailable with
      no fabricated fact (test + live).
- [ ] The loop terminates at the turn cap and at the deadline, and is cancellable — each
      proven by a deterministic test.
- [ ] No Spring AI / Ollama / HTTP type appears in domain or application packages (grep clean).
- [ ] Tracing shows per-turn tool-call events and a final turn count.
- [ ] An eval note records qwen3:4b tool-selection accuracy on a small question set, with a
      recommendation on whether agentic can be the default.

## Milestones

- [ ] M1 — Port + Ollama tool-calling adapter + tool descriptors (controlled contract tests).
- [ ] M2 — `AgenticAnswerUseCase` bounded loop (budget/deadline/cancellation/provenance) with
      deterministic stubbed-LLM tests for every termination and honesty path.
- [ ] M3 — Config `mode` flag + wiring + `agentic-fallback`; deterministic path untouched.
- [ ] M4 — Tracing extension + live verification + qwen3:4b eval note + README/ADR update.

## Documentation impact

- New ADR (next number) recording the decision to add a bounded agentic loop as an opt-in
  capability, the harness constraints, and why the deterministic demo path remains default.
- README: document the `mode` flag and the agentic capability.
- `docs/spec`: note the new orchestration mode and its honesty/boundedness guarantees.
