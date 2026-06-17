# ExecPlan — Component-scan filter, controller wiring & context smoke test

Status: draft — round-1 + round-2 critic reviewed; PLAN-READY (low-severity sharpenings folded below)
Owner: TBD
Source: docs/reviews/2026-06-16-code-quality-audit.md (H-1, SP-1)
Scope: `assistant-app` — `AssistantApplication` (`@ComponentScan`/`@Import`), `adapters/inbound/http`
(ChatController, ChatWebConfiguration), and a new hermetic context-load test. Independent of refactor-1's type
changes (it does NOT touch `ChatHttpMapper` — refactor-1 owns that). Round-1 resolutions marked [R1: Pn-x].

## Why now

`AssistantApplication` declares a **direct** `@ComponentScan(excludeFilters = {ChatController, ChatWebConfiguration})`.
A directly-declared `@ComponentScan` **suppresses** the meta-annotated one from `@SpringBootApplication`
(verified against Spring `ConfigurationClassParser`: the repeatable-scan collection is non-empty, so the
meta-present fallback that carries `TypeExcludeFilter` + `AutoConfigurationExcludeFilter` is skipped). Losing
`TypeExcludeFilter` means `@TestConfiguration` classes under the base package are eligible for component scan in
`@SpringBootTest`. [R1: P5-1] This is a **latent footgun surfaced when attempting SP-1** (a hermetic
context-load test's stub `mcpToolInvoker` collided with the auto-scanned `McpTestConfiguration` — observed
during the FIX-NOW batch). Existing `@SpringBootTest` classes avoid it only because each explicitly `@Import`s
exactly the test config it needs and none does a no-import full-context load. The three-way controller wiring
contraption that motivates the exclude filter is itself fragile.

## Problem (verified against source)

1. **Three-way controller wiring (H-1)**: `ChatController` is `@RestController`, excluded from scan by
   `AssistantApplication.excludeFilters`, and re-created as a `@Bean` in `ChatWebConfiguration` gated on
   `@ConditionalOnBean(AnswerQuestionUseCase.class)`. Three mechanisms for one bean; the `@ConditionalOnBean`
   silently drops the endpoint if the use case is absent (no error).
2. **Scan-filter suppression (root cause)**: the direct `@ComponentScan` drops the default Boot filters.
   `AssistantApplication` also `@Import`s `ChatWebConfiguration` (line 15).
3. **No hermetic context-load test (SP-1)**: the only app-level test asserts an annotation is present; nothing
   proves the default context boots and wires the chat path — which is why the X-C-2 duplicate-Clock bug went
   unnoticed.

## Target state (decisions locked)

- **Restore Spring Boot's default scan filters by deleting the direct `@ComponentScan`.** Adopt the idiomatic
  default: keep `@RestController ChatController` + constructor injection, **delete `ChatWebConfiguration`**, drop
  the exclude-filter block, and remove `@ConditionalOnBean` (in production the use case is always present; a
  missing use case becoming a startup failure is the *desired* behavior, not a silent 404). [R1: P5-6] Enumerate
  the cleanup: remove the `@ComponentScan` block, remove `ChatWebConfiguration` from `AssistantApplication`'s
  `@Import` (line 15), delete `ChatWebConfiguration.java`, delete the now-unused imports (`ChatController`,
  `ChatWebConfiguration`, `ComponentScan`, `FilterType`).
- **Falsifiable scan-leak guard** [R1: P5-1]: add a `@SpringBootTest` context-load test that does **NOT** import
  any `@TestConfiguration`, providing its stub ports via its own local config, and asserts the context boots and
  `ChatController` + `AnswerQuestionUseCase` are present. The PR records that this same test **fails before** the
  scan-filter fix (auto-scanned `@TestConfiguration` beans collide / shadow) and **passes after** — making the
  fix falsifiable rather than circular.
- **Clock / X-C-2 guard (locked) [R1: P5-8]**: in the smoke test assert
  `applicationContext.getBeanNamesForType(Clock.class).length == 1` and that the single bean is the production
  `systemClock` (the X-C-2 fix's surviving bean), with no test Clock imported. This distinguishes a real
  single-Clock wiring from a passing-but-unguarded context (a bare "no bean named `clock`" check is too weak).
- Hermeticity: the test needs no real Ollama/pgvector/MCP/network — stub the external ports via a local
  `@TestConfiguration` the test itself imports (the only test config in its context, now that scanning no longer
  pulls the others).

## Non-goals

- No change to `ChatHttpMapper`, DTOs, or `AnswerSource` shape (refactor-1 owns those).
- No UI/CORS/env work (refactor-5b).
- No change to the request/response JSON contract (docs/spec/14).

## Invariants to preserve

- The chat endpoint is present in production unconditionally; a missing `AnswerQuestionUseCase` is a startup
  failure, not a silent 404.
- Controllers stay inbound adapters only.
- After the fix, `@SpringBootTest` contexts are deterministic: `@TestConfiguration` no longer auto-scans.
- Full suite stays green and hermetic.

## Risks / open questions

- Restoring default scan filters may surface previously-masked autoconfig/bean issues; run the full suite and
  treat any new context failure as a real finding to fix (not to re-mask). Existing tests use `standaloneSetup`
  or explicit `@Import`, so they should be unaffected — verify no duplicate `ChatController` bean.
- Slices: (1) delete `@ComponentScan` + `ChatWebConfiguration` + `@ConditionalOnBean`, fix `@Import`/imports;
  (2) add the falsifiable hermetic smoke test (record before/after).

## Definition of Done (binary)

- [ ] `AssistantApplication` has no direct `@ComponentScan`; `grep -n "excludeFilters\|FilterType" AssistantApplication.java`
      empty; `ChatWebConfiguration.java` deleted; `grep -rn "ChatWebConfiguration" assistant-app/src` empty.
- [ ] Exactly one `ChatController` bean (`@RestController`, constructor-injected); no `@ConditionalOnBean` gates
      the endpoint.
- [ ] A `@SpringBootTest` that imports no `@TestConfiguration` boots, finds `ChatController` +
      `AnswerQuestionUseCase`, and asserts exactly one `Clock` bean (`systemClock`). The PR notes this test fails
      on the pre-fix tree (demonstrating the leak) and passes post-fix.
- [ ] `./mvnw -o test` BUILD SUCCESS and hermetic; no new context failures (or any surfaced are fixed, not
      re-masked).

## Round-2 critic resolutions (PLAN-READY; sharpenings)

- **[P5A-1] Name the concrete pre-fix failure**: with the scan filter dropped, `McpTestConfiguration` (under the
  base package; `@Primary testClock` + stub `mcpToolInvoker`) is auto-scanned, yielding two `Clock` beans — so
  the smoke test's `getBeanNamesForType(Clock.class).length == 1` / `systemClock` assertion fails pre-fix, passes
  post-fix (the falsifiable guard).
- **[P5A-2] Cross-plan note**: the smoke test asserts only bean *presence* + single `Clock`; it does NOT assert
  `AnswerSource`/mapper JSON shape (that is refactor-1's `ChatContractTest`). Either order is safe — refactor-1
  already holds the context-bootable invariant.
- **[P5A-3] DoD add**: `@Import` retains `OrchestrationConfiguration.class` and no longer references
  `ChatWebConfiguration`.
- **[P5A-4] Risk add**: post-fix `McpTestConfiguration` is no longer auto-scanned; the four existing
  `@SpringBootTest` classes that `@Import` it explicitly are unaffected — verify all five stay green.

## Status: PLAN-READY (the only plan cleared by round-2 with no blocking findings).
