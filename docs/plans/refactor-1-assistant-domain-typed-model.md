# ExecPlan — Assistant-app domain typed-model refactor

Status: landed — 8950e95
Owner: TBD
Source: docs/reviews/2026-06-16-code-quality-audit.md (O-1..O-10, O-12, O-13, R-1, M-7, M-10)
Scope module: `assistant-app` — `orchestration`, `question`, `llm`, `tools` packages, the
`adapters/inbound/http` mapper that reads `AnswerSource`, and the adapters that construct `ToolExecutionResult`
/ `LlmResult`. **Not** `rag` package result types (see scope boundary below). No `countries-mcp-server` change.

Round-1 critic resolutions are marked [R1: Pn-x] inline.

## Why now

The orchestration core is the structurally weakest area. State that should be a discriminated union is faked
with parallel optionals, boolean flags + nullable payloads, and a three-variant result type whose third variant
is never consumed distinctly. Behavior is correct and well-covered (AnswerQuestionUseCaseTest 14,
ResponseComposerTest 11, AnswerSourceTest 10, SourceRoutingPolicyTest 8), so this is a behavior-preserving
restructuring with a strong safety net.

## Scope boundary for the shared `SourceUnavailable` shape [R1: P1-1, P1-4, P1-10, P2-9]

The record `SourceUnavailable(String sourceLabel, String message, String hint)` is declared **six** times
(verified): `tools/ToolExecutionResult`, `llm/LlmResult`, `llm/EmbeddingResult`, `rag/RagRetrievalResult`,
`rag/RagIngestionResult`, `rag/ProductPageResult`. This plan introduces the single value object
**`SourceUnavailability`** and adopts it in the **two types this plan owns**: `ToolExecutionResult` and
`LlmResult`. The four `rag`/`llm.EmbeddingResult` adoptions are **owned by refactor-2** (which depends on this
plan landing first). This plan does NOT edit `rag` result types — that would violate the STOP-on-dependency
rule.

Placement: `SourceUnavailability` lives in the **`tools`** package. Cycle check: `question` already imports
`tools.CountryInfo`/`tools.WeatherReport`, so `question → tools`; putting the VO in `tools` lets
`tools`/`llm`/(later)`rag`/`question` all depend on it with no cycle. (Not a new shared-kernel module — that
is reserved for cross-module types, AGENTS §6.)

## Problem (verified against source)

1. **`RoutedQuestion`**: one record, four mutually-exclusive `Optional` fields + `QuestionRoute` tag +
   `validateRouteFields` switch; consumers call `.orElseThrow()` (AnswerQuestionUseCase:98,154,232).
2. **`ToolExecutionResult`**: three variants; the use case collapses `ToolError → SourceUnavailable` with
   identical code at four sites + raw casts (`(ToolExecutionResult.Success<CountryInfo>) result).value()`,
   lines 130,171). The use case treats ToolError and SourceUnavailable identically at every site.
3. **`AnswerSource.CountriesFacts`/`WeatherObservation`**: nullable payload + redundant boolean
   (hasResolved*) mirroring status==USED + ~20 lines of compact-constructor guards. `RagKnowledge`,
   `ModelSynthesis` share the status-ladder shape. `SourceContributionStatus` enum is the discriminator the
   HTTP JSON depends on.
4. **`AssistantAnswer`**: `storedTraceCorrelationId` + `hasTraceCorrelationId` boolean + three guards.
5. **`ResponseComposer`**: 14 compose* methods; `composeCountriesUnavailable` and
   `composePlaceSynthesisCountriesUnavailable` produce byte-identical output.
6. **Duplicated/ignored data**: `CAPITAL_FACT_TEMPLATE` in both AnswerQuestionUseCase:256 and
   ResponseComposer:25; both `SourceUnavailable` types carry a `sourceLabel` the composer ignores in favour of
   hardcoded label constants (O-4); synthesis sub-flow duplicated in handlePlaceSynthesis/handleCdqProduct (O-6).

## Target state (decisions locked) [R1: P1-3, P1-5, P1-6, P1-7, P1-8]

- **`RoutedQuestion` → sealed interface**, one record per route carrying exactly its data. `validateRouteFields`
  and the four optionals/`orElseThrow` disappear; `answer` dispatches with an exhaustive pattern switch.
  **Decision: KEEP the `QuestionRoute` enum** [R1: P1-6] — it backs the trace `route=` log field and
  `outcomeLabel` (`route.name().toLowerCase()`). Each sealed variant exposes its `QuestionRoute route()`. This
  preserves the exact trace/outcome tokens (behavior-preserving) without pinning fragile new label strings.
- **`SourceUnavailability` VO** in `tools` (sourceLabel, message, hint; existing null/blank validation). The
  `SourceUnavailable` variant of `ToolExecutionResult` and `LlmResult` carries one `SourceUnavailability`.
- **`ToolExecutionResult` coalescing** [R1: P1-8]: **Decision: keep three variants** (Success, ToolError,
  SourceUnavailable). Justification on present grounds: adapters today produce both ToolError (tool ran, returned
  an error envelope) and SourceUnavailable (transport/source down) and may log them differently; R-19 wants the
  error message preserved. The use case stops distinguishing them at *consumption* via a coalescing view
  `SourceUnavailability ToolExecutionResult.asUnavailability(String fallbackLabel)` (returns the VO for either
  failure, preserving ToolError's message/hint). The four duplicated blocks and the raw casts are deleted; the
  problem statement's "treated identically" refers to consumption only, not production.
- **`AnswerSource.*` → sealed pairs** [R1: P1-5]: each of `CountriesFacts`, `WeatherObservation`, `RagKnowledge`,
  `ModelSynthesis` becomes a sealed type with explicit variants (`Used(payload)` and
  `Unavailable(SourceUnavailability)`; `RagKnowledge` additionally `Insufficient`). The boolean flags, nullable
  payloads, and cross-field guards are removed; invalid states are unrepresentable **by construction**. The
  inconsistency unit tests become compile-fail fixtures or are replaced by tests proving a `Used` cannot carry
  an unavailability. The single-record-with-nullable-VO shape is explicitly forbidden.
- **`AssistantAnswer`**: trace correlation id modeled as typed absence (a present-or-absent value, no boolean
  flag, no guards).
- **HTTP JSON contract is invariant** [R1: P1-2, P1-3, P5-2]: this plan **owns** the `ChatHttpMapper` update.
  After the `AnswerSource` sealed migration, the mapper is rewritten to derive the **byte-identical** JSON
  (`type`, `status`, `unavailableMessage`, `unavailableHint`, `countryInfo`, `weatherReport`, `snippets`,
  `timestamp.kind`) from the new shape. `ChatContractTest`'s JSON-shape assertions stay unchanged.
  **Decision**: `SourceContributionStatus` survives as the JSON `status` discriminator, derived from the sealed
  subtype (`Used`→USED, `Unavailable`→UNAVAILABLE, `Insufficient`→INSUFFICIENT). refactor-5 does NOT touch the
  mapper; this plan is its sole owner (resolves the cross-plan conflict).
- **`ResponseComposer`**: collapse byte-identical per-route unavailable methods into one keyed by present
  contributions; render the failing source's label from `SourceUnavailability.sourceLabel()` (O-4), not
  hardcoded constants.
- **Synthesis sub-flow (O-6)** extracted to one private helper (grounded facts, instructions, two outcome
  composers). `CAPITAL_FACT_TEMPLATE` gets one owner (O-5).
- **`AssistantRequestTrace` (O-10)** [R1: P1-7]: **Decision: keep as a deliberate per-request logging sink**;
  document the mutation as intentional and confined, name the `"pending"` and outcome-format literals, and move
  outcome-string formatting out of the use case into the trace. The mutable-accumulator restructure into an
  immutable id + logger is explicitly **out of scope** (request-local per ADR 0006; the churn is not justified
  by a current requirement). O-10's "immutability" recommendation is consciously declined and recorded here.
- **O-12 (SourceRoutingPolicy)** [R1: P1-9]: add a **characterization** test (not a fix) documenting that
  routing is demo-scoped — assert an off-demo input ("capital of France") falls through to `UNSUPPORTED` and does
  not return a Germany answer. Stated explicitly as pinning current behavior, plus a code comment that routing is
  scoped to the fixed demo questions. No routing logic change.

## Non-goals

- No new behavior/routes; deterministic routing decisions unchanged; **every answer text and every JSON byte the
  API returns is unchanged** (the mapper rewrite reproduces the identical contract — proven by unchanged
  `ChatContractTest` assertions).
- No cross-module shared-kernel module; no edits to `rag` result types (refactor-2 owns those).

## Invariants to preserve

- Constructed `AnswerSource`/`AssistantAnswer`/`RoutedQuestion` always valid (structurally, not by guards).
- Every source-unavailable path returns a typed response naming the failed source; never answers from model
  memory; `ModelSynthesis` stays distinct from tool/RAG sources (AGENTS §8).
- No Spring AI/Ollama/MCP/JDBC types in orchestration/question/llm (ADR 0001; confirmed clean O-14).
- Trace `route=` and `outcome=` log tokens unchanged (QuestionRoute retained).
- Exhaustive switches, no `default` arm.

## Risks / open questions

- Ordered, independently-green sub-slices: (1) `SourceUnavailability` VO + adopt in ToolExecutionResult/LlmResult
  + `asUnavailability`; (2) `RoutedQuestion` sealed (keep QuestionRoute); (3) use-case switch cleanup (delete dup
  blocks + casts); (4) `AnswerSource`/`AssistantAnswer` sealed + **ChatHttpMapper rewrite** + ChatContractTest
  stays green; (5) `ResponseComposer` collapse + label-from-source; (6) synthesis extraction + literal de-dup +
  O-12 characterization test + AssistantRequestTrace literal-naming.
- Slice 4 is the contract-sensitive one; the unchanged `ChatContractTest` JSON assertions are the guard.
- refactor-5 must not start until... no dependency the other way: refactor-5's scan/wiring work is independent
  of these types; only the mapper is shared and is owned here.

## Definition of Done (binary)

- [x] `grep -n "Optional<" orchestration/RoutedQuestion.java` empty; `validateRouteFields` gone; `RoutedQuestion`
      is a sealed interface; `QuestionRoute` retained and each variant exposes `route()`.
- [x] `grep -rn "orElseThrow" orchestration/` empty; `grep -rn "instanceof ToolExecutionResult\|(ToolExecutionResult.Success" src/main` empty.
- [x] `SourceUnavailability` declared once (in `tools`); `ToolExecutionResult` and `LlmResult` `SourceUnavailable`
      variants carry it; `grep` shows rag result types untouched by this plan.
- [x] `AnswerSource` subtypes are sealed `Used`/`Unavailable`(/`Insufficient`); no `boolean has*` field, no
      nullable payload field; a fixture proving a `Used` cannot hold an unavailability does not compile.
      (Note: invariant is structurally enforced by the sealed record shape; documented in `AnswerSourceTest`.)
- [x] `AssistantAnswer` has no `hasTraceCorrelationId` boolean.
- [x] `CAPITAL_FACT_TEMPLATE` literal in exactly one file.
- [x] ResponseComposer: the two countries-unavailable methods are one. (O-4 label-from-VO dropped per Round-2 [F2].)
- [x] **`ChatContractTest` JSON-shape assertions are unchanged** (diff is purely additive — only the [F4]
      characterization test added); `ChatHttpMapper` stayed byte-compatible (accessor-preserving design).
- [x] O-12 characterization test present and green.
- [x] `./mvnw -o test` BUILD SUCCESS, test count ≥ current (assistant-app 178 ≥ 169); no behavioral assertion weakened.

## Round-2 critic resolutions (authoritative; supersede the body where in conflict)

- **[F3, blocking] Slice 1 re-scoped.** Changing the `SourceUnavailable` record to carry one `SourceUnavailability`
  fans out immediately to 8 sites (CountriesMcpResponseMapper:61,112; WeatherMcpResponseMapper:55,78;
  OllamaLlmAdapter:39,44; ResponseComposer:52,69,97,128,137,181), so it is NOT independently green. Revised
  slices: **(1)** introduce the `SourceUnavailability` VO type only + add `ToolExecutionResult.asUnavailability(...)`
  and `LlmResult` equivalent computed from the *existing* `sourceLabel`/`message`/`hint` fields (no record-field
  change yet). The record-field migration (replacing the three loose fields with one VO) merges into the slice
  that rewrites ResponseComposer + the MCP/Ollama adapters together (was "slice 5"); that slice explicitly lists
  the 8 fan-out sites and is green as a unit.
- **[F2, blocking] O-4 dropped from this plan.** Rendering the failing label from `SourceUnavailability.sourceLabel()`
  is a behavior change: the VO label is lowercase `"countries MCP"` (from the mapper) while the composer constant
  is title-case `"Countries MCP"`; switching the source would change user-visible answer text, violating the
  byte-identical-answer non-goal. O-4 (label unification incl. casing reconciliation) is deferred to a dedicated
  follow-up. ResponseComposer keeps its hardcoded label constants; the "render label from VO" DoD bullet is removed.
- **[F1] use-case Success extraction.** `handleCountryThenWeather` and `handlePlaceSynthesis` convert their
  `instanceof` ladders to exhaustive `switch` whose `Success` arm binds the payload; `asUnavailability` is used
  only on failure arms. No residual cast (satisfies the no-cast/no-instanceof DoD grep).
- **[F5] Factory names preserved.** `CountriesFacts.used(...)`, `.unavailable(...)`, `RagKnowledge.insufficient()`,
  `ModelSynthesis.used()/.unavailable()` remain as static factories on the sealed parents (returning the right
  variant), so the 12 ResponseComposer construction sites and ChatContractTest construction calls are unchanged.
- **[F4] Contract characterization added.** Before the sealed migration, add to `ChatContractTest` an assertion
  that an `Unavailable` source over HTTP emits exactly `unavailableMessage` + `unavailableHint` and omits
  `countryInfo`/`snippets` — pinning the `blankToNull`/`@JsonInclude(NON_NULL)` behavior the migration must keep.
