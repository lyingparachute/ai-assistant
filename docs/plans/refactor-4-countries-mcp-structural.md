# ExecPlan — countries-mcp-server structural cleanup

Status: landed — 1efb9d9
Owner: TBD
Source: docs/reviews/2026-06-16-code-quality-audit.md (C-1..C-8, C-10, C-11, C-12)
Scope module: `countries-mcp-server` only. The MCP tool name (`country_lookup`), input schema, and output JSON
envelope shape must not change. Round-1 resolutions marked [R1: Pn-x].

## Why now

The server leaks recovery-hint text (an MCP presentation concern) into its domain `CountryLookupOutcome`,
duplicates that text in a second class (`CountryToolErrors`) that also reaches up into the application package
by FQN, and wraps the use case in a pure pass-through service. Two sources of truth for the same strings and a
wrong-direction dependency. Behavior is covered by `CountryToolContractTest` (6), `CountryLookupIntegrationTest`
(6), lifecycle/app tests.

## Problem (verified against source)

1. **Hint split + dup + FQN reach-up (C-1/2/3)**: `CountryLookupHints` (application) holds the hint constants +
   `ambiguousCapital()`; `CountryLookupOutcome` variants store a `hint` field; `CountryToolErrors` (support)
   re-declares byte-identical `HINT_NOT_RECOGNIZED`/`HINT_SOURCE_UNAVAILABLE` and reaches up via FQN
   `dev.localassistant.countries.application.CountryLookupHints.ambiguousCapital(...)` (CountryToolErrors:37).
2. **Pass-through service (C-4)**: `CountriesApplicationService.lookupCountry(name)` is exactly
   `lookupCountryUseCase.lookup(LookupPlace.of(name))`, with its own bean (BeansConfig:42). `LookupCountryUseCase`
   takes a `LookupPlace`, not a `String`.
3. **Duplicated switch arms (C-5)**: `resolveNameLookup`/`resolveCapitalLookup` share identical
   SourceUnavailable + NotFound arms (35-38, 51-54).
4. **Double error-flag (C-10)**: `fromOutcome` switches the outcome once; `isErrorOutcome` does a second
   `!(instanceof Success)` pass; both called separately in `handle` (CountryLookupTool:41).
5. **Self-recursive internal-failure (C-11)**: on `JsonProcessingException`, `toCallToolResult` recurses with
   `internalFailure()`; cause swallowed, no stderr log. [R1: P4-4] Note: the fallback envelope is `Map.of` of
   strings/booleans → trivially serializable, so recursion terminates; the defect is the swallowed cause + the
   self-referential shape, not a live infinite loop. stderr logging is a clean-java/AGENTS §8 no-silent-failure
   requirement, not a literal contract clause.
6. **Wiring/packaging (C-6/7/8/12)**: MCP request timeout = `restCountriesTimeoutSeconds * 2L` (Factory:39),
   undocumented; `mapCountry` uses FQN `Optional` + manual null guards; domain split across technical
   `model`/`schemas`/`support` packages; `CountryLookupTool` hand-built in the factory.

## Target state (decisions locked)

- **Hint text owned solely by the tools layer** [R1: P4-1/2/3]: `CountryLookupOutcome` variants carry only
  discriminator + data (NotRecognized/SourceUnavailable become marker/data records with **no hint**;
  AmbiguousCapital keeps only `List<String> countryNames`). `CountryToolErrors.fromOutcome` maps each arm to its
  error + hint — the single source of truth. **`CountryLookupHints` is deleted.**
  - The **real guard is the in-module `CountryToolContractTest`** (NOT assistant-app's
    `CountriesMcpClientContractTest`, which uses inline JSON literals and runs no server code — it cannot detect
    a hint regression). `CountryToolContractTest` currently *references* `CountryLookupHints.SOURCE_UNAVAILABLE`
    and passes a `hint` into the `AmbiguousCapital` constructor, so deleting `CountryLookupHints`/the hint fields
    **breaks its compilation** — it MUST be updated (this is in scope, not "unchanged"): re-point it at
    `CountryToolErrors.HINT_SOURCE_UNAVAILABLE` / `CountryToolErrors.ambiguousCapital(...)`, with the asserted
    **string values byte-identical**.
  - **Invariant: byte-identical hint strings** [R1: P4-3]. Enumerate and pin the five exact strings: name-required
    (`CountryToolErrors.HINT_NAME_REQUIRED`), not-recognized (`HINT_NOT_RECOGNIZED`), source-unavailable
    (`HINT_SOURCE_UNAVAILABLE`), the ambiguous-capital template, and the internal-failure message. "Move, do not
    reword" — `isEqualTo` assertions stay on the same literal values.
- **Collapse the pass-through** [R1: P4-5]: **Decision — delete `CountriesApplicationService`; the
  `CountryLookupTool` depends on `LookupCountryUseCase` directly and constructs `LookupPlace.of(name)` itself**
  (the tool already validates the blank name, so VO construction beside it is coherent; the use case keeps its
  pure `lookup(LookupPlace)` signature — no API churn). Update `CountryLookupIntegrationTest` and
  `CountryToolContractTest` (which reference the service) accordingly. Extract the shared
  SourceUnavailable/NotFound switch arms once, parameterizing the Success arm.
- **Error flag + payload in lockstep (C-10)**: `fromOutcome` returns one value carrying both the payload map and
  the `isError` flag computed in a single switch; the second `instanceof` pass is removed.
- **Internal-failure non-recursive + logged (C-11)**: serialize the static fallback via a non-throwing path and
  log the original `JsonProcessingException` to stderr before returning it.
- **MCP request-timeout property (locked) [R1: P4-7]**: replace `restCountriesTimeoutSeconds * 2L` with a typed
  property `countries.mcp.request-timeout-seconds`, env `COUNTRIES_MCP_REQUEST_TIMEOUT_SECONDS`, **default 20**
  (preserving today's 10×2 effective value), validated `@Positive`. Add a row to docs/spec/11's config table;
  disambiguate in the plan text from the *client-side* `ASSISTANT_MCP_COUNTRIES_TIMEOUT_SECONDS` (spec 12).
- **`mapCountry` (locked) [R1: P4-8]**: import `Optional`; let `CountryFacts` construction own blank/negative
  validation. **Keep the explicit `hasNonNull("population")` presence check** — `CountryFacts` accepts
  population 0, so relying on `asLong()` (which defaults missing → 0) would turn a missing-population node into a
  valid 0-population success instead of dropping it to source-unavailable (spec 11:106). Add an AC asserting a
  node missing `population` does NOT become a 0-population success.
- **Wiring/packaging**: `CountryLookupTool` becomes a managed bean injected into the factory (single composition
  root). Repackage-by-capability (C-8) is the **lowest priority** and is an explicit **deferred non-goal** for
  this plan (tracked below), since it is a broad behavior-neutral rename — do it as a separate change.

## Non-goals

- No change to the MCP tool name, input schema, or output JSON envelope shape (assistant-app's countries client
  contract test is run as a **non-regression smoke check**, not the guard).
- No change to REST Countries query behavior or ambiguity rules.
- C-8 repackaging deferred (tracked follow-up in this plan's footer).

## Invariants to preserve

- Tool never fabricates; source-unavailable / not-recognized / ambiguous / name-required stay distinct typed
  outcomes; hint strings **byte-identical** to today (AGENTS §8, MCP skill).
- stderr-only logging (no stdout pollution of the stdio transport).
- Dependencies point inward; after the change no support/tool class references the application package.
- Domain/application import no Spring/MCP-SDK/HTTP types.
- `mapCountry` missing-required-field behavior unchanged (missing population → source-unavailable, not a
  0-population success).

## Risks / open questions

- The hint-ownership move forces edits to `CountryToolContractTest` (compile-break on `CountryLookupHints`); the
  byte-identical-string invariant + `isEqualTo` assertions are the guard. Ordered slices: (1) hint ownership →
  tools, delete `CountryLookupHints`, update the contract test; (2) collapse app service + shared switch arms +
  update tests; (3) error-flag lockstep; (4) internal-failure non-recursive + stderr log; (5) timeout property +
  spec 11; (6) `mapCountry` cleanup with the population guard; (7) `CountryLookupTool` bean wiring.
- Default timeout must stay 20s to avoid a silent behavior change.

## Definition of Done (binary)

- [x] `CountryLookupHints` deleted; `grep -rn "CountryLookupHints" countries-mcp-server/src` (whole src, not just
      main) returns nothing; hint strings declared once in `CountryToolErrors`.
- [x] `CountryLookupOutcome` variants carry no `hint` field.
- [x] `grep -rn "application\." countries-mcp-server/src/main/java/.../support` empty (no reach-up).
- [x] `CountriesApplicationService` deleted; `CountryLookupTool` depends on `LookupCountryUseCase`; the
      duplicated NotFound/SourceUnavailable arms exist once. Affected tests updated and green.
- [x] `CountryToolContractTest` asserts the five hint/message strings by exact equality at their unchanged
      literal values; `CountryLookupIntegrationTest` green.
- [x] `CountryToolResult` switches the outcome exactly once (via `ToolEnvelope`); no separate `isErrorOutcome` instanceof pass.
- [x] JSON-failure path is non-recursive and logs the cause to stderr via SLF4J (logback STDERR appender).
- [x] `grep -n "\* 2L\|\* 2 " core/CountriesMcpServerFactory.java` empty; `countries.mcp.request-timeout-seconds`
      exists (default 20, `@Positive`, 5th component on `CountriesMcpConfiguration`); docs/spec/11 config table updated.
- [x] A test asserts a country node missing `population` maps to source-unavailable, not a 0-population success.
- [x] assistant-app countries client contract test still passes (non-regression smoke).
- [x] `./mvnw -o -pl countries-mcp-server test` BUILD SUCCESS (21 tests); full reactor green.

## Tracked follow-up (out of scope here)

- C-8: repackage `model`/`schemas`/`support` by business capability. Behavior-neutral rename; do as a dedicated
  change after this plan lands.

## Round-2 critic resolutions (authoritative; supersede the body where in conflict)

- **[P4R2-1, blocking] `LookupCountryUseCase` is the real hint producer — add it to scope.** It calls
  `CountryLookupHints.SOURCE_UNAVAILABLE/NOT_RECOGNIZED/ambiguousCapital(...)` at six sites and stuffs them into
  the outcome records. Deleting `CountryLookupHints` + the outcome hint fields breaks production unless the use
  case is rewritten. Locked decision: `LookupCountryUseCase` returns hint-free outcomes
  (`new CountryLookupOutcome.SourceUnavailable()`, `.NotRecognized()`, `.AmbiguousCapital(countryNames)`); the
  tools layer (`CountryToolResult.fromOutcome`) derives every hint via `CountryToolErrors`. Slice 1 includes the
  use-case rewrite. DoD grep over `src` (already broadened) catches any residual `CountryLookupHints` reference.
- **[P4R2-2, blocking] Ambiguous-capital template relocated verbatim.** That template string lives ONLY in
  `CountryLookupHints.ambiguousCapital()` today (`CountryToolErrors` reaches up to it). It must be physically
  moved, byte-identical (the `", "` join + `". Provide the country name instead."` suffix), into
  `CountryToolErrors.ambiguousCapital(List<String>)`; the FQN reach-up is then deleted.
- **[P4R2-3] Contract test must ADD exact-equality assertions for all five strings.** `CountryToolContractTest`
  today exact-asserts only the not-recognized and source-unavailable hints; it asserts error *codes* elsewhere.
  Slice 1 updates it to (a) migrate the `CountryLookupTool` mock from `CountriesApplicationService` to
  `LookupCountryUseCase` (lines 24,101), (b) drop the 2-arg hint `AmbiguousCapital` constructor usage, (c)
  exact-assert HINT_NAME_REQUIRED, HINT_NOT_RECOGNIZED, HINT_SOURCE_UNAVAILABLE, the full ambiguous template, and
  the internal-failure message.
- **[P4R2-4] `CountriesMcpBeansConfiguration` rewire in scope.** Drop the `countriesApplicationService` bean; add
  a `countryLookupTool` bean (`LookupCountryUseCase`, `ObjectMapper`); change the `countriesMcpServerFactory`
  bean to inject `CountryLookupTool`. DoD: `grep -rn "CountriesApplicationService" countries-mcp-server/src` empty.
- **[P4R2-5] Timeout property is a 5th component on the `CountriesMcpConfiguration` record** (`@Positive int
  requestTimeoutSeconds`, default 20); `CountryLookupIntegrationTest` (4-arg `new CountriesMcpConfiguration(...)`)
  must pass the new arg.
- **[P4R2-7] Name the error-flag carrier**: `CountryToolResult.fromOutcome` returns a package-private
  `record ToolEnvelope(Map<String,Object> payload, boolean isError)` computed in one switch (`Success`→false,
  all others→true); `handle` consumes it; the separate `isErrorOutcome` pass is deleted.
