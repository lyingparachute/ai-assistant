# Code-Quality Audit — 2026-06-16

Branch: `review/code-quality-audit`. Six parallel reviewers covered every code-bearing
commit, partitioned by subsystem. ~75 findings. This document is the triage of record.

Note: the prompt referenced a root `PLANS.md`; none exists. Follow-up ExecPlans follow the
repo's existing convention under `docs/plans/` plus the plan-quality rules in the global
agent rules.

## Triage policy

- **FIX-NOW** — contained, high-confidence correctness/hygiene fixes with low blast radius.
  Implemented + tested + committed on this branch.
- **ExecPlan** — large or interlocking structural reframings ("judo" moves). The biggest wins,
  but they cross many files and types. Each gets a critic-reviewed (2×) ExecPlan under
  `docs/plans/` and is executed as a unit, not piecemeal, to avoid partial-state churn.

## FIX-NOW batch (this branch)

| ID | Severity | Location | Fix |
|----|----------|----------|-----|
| X-C-1 | BLOCKER | AssistantRagProperties.java:23-27 | Remove hardcoded DB username/password Java defaults; keep `@NotBlank`, source from yml/env. |
| X-C-2 | BLOCKER(latent) | RagApplicationConfiguration / McpOutboundConfiguration | Collapse to one profile-neutral `Clock` bean (deleted `McpOutboundConfiguration.clock()`). |
| M-1 | BLOCKER | StdioMcpToolInvoker.java | Implement `DisposableBean`; `closeGracefully()` every MCP subprocess client on shutdown. |
| M-2 | HIGH | Countries/Weather McpResponseMapper | Honor MCP `isError`: error response with unparseable content → `SourceUnavailable`. |
| H-6 | HIGH | HttpExceptionHandler.java:35-39 | Stop blanket-mapping `IllegalArgumentException`→400 (leaks internal invariant messages, hides server bugs). Rely on `@NotBlank`/bean validation for the real 400; let other IAE fall to 500. |
| M-4 | HIGH | StdioMcpToolInvoker.java:66-69 | ~~Pass configured `workingDirectory`~~ — **NOT FIXABLE**: MCP SDK 1.0.0 `ServerParameters.Builder` has no cwd setter (confirmed via `javap`). Moved to ExecPlan 4: either SDK upgrade or launch subprocess via a `cwd`-setting wrapper command; until then the `working-directory` property is dead and must be marked/removed. |
| X-B-1 | MEDIUM | assistant-app/pom.xml:48-51 | Move jsoup version to parent pom `<properties>`. |
| M-5 | MEDIUM | WeatherMcpResponseMapper.java:60 | Delete unreachable ternary + dead `parsedCity`. |
| M-11 | LOW | WeatherMcpClientAdapter.java:36-37 | Trim location once; pass canonical value to both call and mapper. |
| H-2 | MEDIUM | ChatController / ChatHttpMapper | Stop `new`-ing the mapper in a bean; make it a component or static. |
| C-9 | LOW | StubRestCountriesServer.java:42-44 | Delete unused `stubDelayForAllRoutes`. |
| R-7 | MEDIUM | RagIngestionReport.java | Delete never-produced `Outcome.SKIPPED` + its branch + negative test. |
| R-8 | MEDIUM | OllamaEmbeddingAdapter.java:49 | Narrow `catch (Exception)`→`RuntimeException` (the embed call declares no checked/`InterruptedException`, so nothing to re-interrupt). |
| R-14 | LOW | OllamaEmbeddingAdapter.java:20-24 | `Objects.requireNonNull` constructor args. |
| R-15 | LOW | use case + adapters | Mark `CdqProductKnowledgeAdapter`, `ProductPageTextExtractor` (and where compatible) `final`. |
| R-19 | LOW | CdqProductKnowledgeAdapter.java:36-44 | Preserve `getMessage()` in IO-branch `SourceUnavailable`. |
| X-O-1 | MEDIUM | .gitignore | Add `*.iml`. |

## ExecPlans (deferred, each critic-reviewed 2×)

Plan files under `docs/plans/` (round-1 + round-2 critic reviewed; round-2 blockers folded into each plan's
"Round-2 critic resolutions" addendum):

- `refactor-1-assistant-domain-typed-model.md` — sealed RoutedQuestion/AnswerSource/AssistantAnswer,
  ToolExecutionResult coalescing, ResponseComposer collapse, owns the ChatHttpMapper rewrite, SourceUnavailability VO.
- `refactor-2-rag-ingestion-port-redesign.md` — typed 3-method port, atomic outcome, KnowledgeSnippet score VO.
  Depends on refactor-1 (SourceUnavailability VO).
- `refactor-3a-e2e-demo-honesty-gating.md` — failsafe opt-in, remove `assumeTrue`, single demo-question source. **Ship first.**
- `refactor-3b-config-records-and-build-hygiene.md` — validated record `@ConfigurationProperties`, dedup, scripts.
- `refactor-4-countries-mcp-structural.md` — hint ownership to tools layer, collapse pass-through, timeout property.
- `refactor-5a-scan-filter-and-controller-wiring.md` — **PLAN-READY** (restore Boot scan filters, collapse controller
  wiring, hermetic context-load smoke test).
- `refactor-5b-chat-ui-safety-and-cors.md` — UI escaping, CORS to typed properties.

Conceptual cluster mapping (original numbering retained below for traceability):


1. **Orchestration typed-model refactor** — `RoutedQuestion` sealed union (O-7, drops 4 Optionals +
   `validateRouteFields` + every `orElseThrow`), `AnswerSource`/`AssistantAnswer` sealed `Used`/`Unavailable`
   (O-3, O-9), `ToolError`⇒`SourceUnavailable` coalescing helper (O-1, kills 4 dup blocks + casts),
   synthesis sub-flow extraction (O-6), `ResponseComposer` matrix collapse (O-2), render label from source
   (O-4), `AssistantRequestTrace` immutability (O-10), `CAPITAL_FACT_TEMPLATE` dedupe (O-5),
   `SourceRoutingPolicy` scope doc + off-demo test (O-12), unused import (O-11, trivial — may fold to FIX-NOW).
2. **Shared `SourceUnavailability` value object** — unify the 5 duplicated `SourceUnavailable(label,message,hint)`
   records (R-1) + cause-appropriate hints (M-7, M-10). Cross-cuts tools/rag/llm; coordinate with plan 1.
3. **RAG ingestion/port redesign** — collapse read methods into store contract, single error contract,
   atomic outcome (R-3/R-4/R-5/R-6); `KnowledgeSnippet` score typed-absence (R-2); test-config dedupe (R-13);
   schema-init path (R-12); chunker/SQL magic literals (R-10/R-11); chunker overlap tests (R-20); retrieval
   source-unavailable test (R-21); `RagIngestionMode` testability (R-18); `RagIngestionUseCase` pattern switch
   + Optional idiom (R-16/R-17).
4. **Config consistency & build hygiene** — assistant-app `@ConfigurationProperties` → validated records
   (C-6/C-7/R-9), single ownership of each props type (X-C-4), cross-field chunk validation (X-C-5),
   single ollama base-url (X-C-3), env-passthrough into per-server config (M-3), scripts hardening
   (S-1 pin remote weather repo, S-2 toolchain, S-3 derive jar path).
5. **Countries-mcp-server structural** — hint ownership into tools layer + delete `CountryLookupHints`
   (C-1/C-2/C-3), collapse pass-through app service (C-4/C-5), MCP request-timeout own property (C-6),
   error-flag/payload lockstep (C-10), internal-failure non-recursive + logged (C-11), tool bean wiring (C-12),
   package-by-capability (C-8), restcountries mapping cleanup (C-7).
6. **Chat HTTP & UI cleanup** — collapse 3-way controller wiring + delete `ChatWebConfiguration` (H-1)
   AND restore Spring Boot's default component-scan filters (the custom `@ComponentScan(excludeFilters)`
   on `AssistantApplication` drops `TypeExcludeFilter`, so `@TestConfiguration` classes leak into every
   `@SpringBootTest`); then add the hermetic default-profile context-load smoke test (SP-1) that guards
   X-C-2 — SP-1 is blocked on this filter fix,
   mapper boilerplate collapse (H-3/H-4/H-5), real contract test incl. error paths (H-7), UI DOM-build vs
   innerHTML (H-9), CORS policy to typed properties (H-10), env default doc (H-8), DTO indirection accepted +
   documented (H-11).
7. **e2e demo verification** — move `RequiredDemoQuestionsE2ETest` off surefire to opt-in failsafe `*IT` /
   profile-gated module so the build never reports demo verification as green without a real run
   (E-1, B-2); single source for demo question set (X-M-1).
