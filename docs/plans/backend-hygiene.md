# ExecPlan — backend hygiene (dependencies, Commons Lang, selective Lombok)

Status: landed — c866bc2
Owner: TBD
Source: recruitment focus on backend quality; user-directed scope (Option A — hygiene only);
`docs/reviews/2026-06-16-code-quality-audit.md` residual style tail; `implementation-notes.md`
Java micro-style preferences (`StringUtils`, `Objects`, `final var`).
Scope modules: parent `pom.xml`; `assistant-app`; `countries-mcp-server`. `e2e-tests` and
`chat-ui` are out of scope unless a dependency bump forces a compile fix.

## Why now

Phases 2–7 and refactors 1–5b are landed; the demo path is green. The next visible gap for a
**backend hire** is not another feature — it is code that reads like deliberate, modern Java:
current dependency patches, consistent null/blank handling, less logging boilerplate, and
documented conventions reviewers can grep for. UX work (streaming trace) and capability work
(agentic loop, package restructure) stay deferred.

Verified baseline (2026-06-17):

| Dependency | Current (root `pom.xml`) | Notes |
| --- | --- | --- |
| Spring Boot parent | `3.5.9` | Latest **3.5.x** patch is `3.5.12` (Mar 2026) |
| Spring AI BOM | `1.1.2` | Latest **1.x** is `1.1.8`; **2.0.0** requires Boot **4.x** |
| MCP SDK BOM | `1.0.0` | **2.0.0** ships with Spring AI 2 / Boot 4 — not compatible with current stack |
| jsoup | `1.19.1` | Explicit property; bump to latest `1.x` patch in M1 |
| Java | `21` | Unchanged |
| Lombok | absent | No generated code today |
| Commons Lang | absent | Blank/null checks use JDK `String.isBlank()`, `== null`, `requireNonNull` |
| Manual `LoggerFactory` | 5 production classes | `HttpExceptionHandler`, `StdioMcpToolInvoker`, `AssistantRequestTrace`, `CountryLookupTool`, `CountriesMcpServerAdapter` |

## Target state

A single hygiene pass that leaves behaviour, routing, honesty, and API contracts **unchanged**
while making the Java modules easier to read and audit.

### Locked decisions

1. **Patch-line dependency bumps only — no Boot 4 / Spring AI 2 migration in this plan.**
   Spring Boot `3.5.9` → `3.5.12`; Spring AI `1.1.2` → latest `1.1.x` compatible with that
   parent; MCP SDK stays on `1.0.x` BOM unless a newer `1.0` patch exists without forcing AI 2;
   jsoup → latest `1.x` patch. Rationale: Boot 4 + Spring AI 2 + MCP SDK 2 + Jackson 3 is a
   platform migration (weeks, cross-cutting), not hygiene. Record it as a **follow-up plan**
   with its own ADR if pursued later.

2. **Apache Commons Lang 3 — explicit dependency in both Java modules.**
   Version is managed by the Spring Boot parent BOM (no separate parent
   `<dependencyManagement>` entry required). Both `assistant-app` and
   `countries-mcp-server` declare `commons-lang3` at `compile` scope.

3. **String handling rules (production code in both Java modules).**

   | Situation | Use |
   | --- | --- |
   | Nullable external / adapter input, blank means invalid | `StringUtils.isBlank(s)` |
   | Normalize user or tool input before validation | `StringUtils.stripToNull(s)` then validate |
   | Required non-null parameter / record component | `Objects.requireNonNull(x, "name")` (keep — already idiomatic) |
   | Optional readability for null checks on non-strings | `Objects.isNull` / `Objects.nonNull` where it clarifies control flow |
   | Required non-blank after trim (domain boundary) | trim via `StringUtils`, then `isNotBlank` or throw — **do not** mix `isBlank` on untrimmed user text |

   **Do not** replace every `text.isBlank()` on already-validated domain strings (e.g.
   `UserQuestion` after construction) — that is noise. Focus on **adapter boundaries**,
   **MCP/HTTP mappers**, **config guards**, and **nullable tool payloads** (~25 production
   files touched per grep, not a blind全-repo sweep).

4. **Lombok — backend conventions; records stay plain Java.**

   Allowed:

   - `@Slf4j` on classes that today declare `private static final Logger log =
     LoggerFactory...` (the five production classes above).
   - `@RequiredArgsConstructor` on Spring `@Component` / `@Configuration` types that inject
     **`final` dependencies only** via constructor — replaces hand-written one-liner
     constructors. **Excluded (keep explicit constructors):** `StdioMcpToolInvoker` (dual
     constructors — production `@Autowired` + test-only `Map` ctor; Lombok collision),
     any bean with constructor validation/normalization logic.
   - `@Builder` on **immutable classes** (not records) where staged construction reads better
     than a long constructor list — e.g. test fixtures, stub ports, mapper helpers touched in
     this plan. Invariants must still hold on the built object (`@Builder.Default`, custom
     package-private constructor on the builder product, or a static factory that validates
     after `build()`). Do **not** use `@Builder` on `@ConfigurationProperties` or domain value
     objects that are already `record`s.
   - `@UtilityClass` on types whose API is **entirely static** — replaces `final class` +
     `private` empty constructor boilerplate. Production candidates verified in source:

     | Module | Class |
     | --- | --- |
     | `assistant-app` | `ContentHasher`, `EmbeddingDimensions`, `PgvectorEmbeddingCodec`, `PgvectorChunkColumns`, `RagIngestionMode` |
     | `countries-mcp-server` | `CountryToolErrors`, `CountryToolSchemas`, `CountryToolResult` |

     `CountryToolResult` contains a nested package-private `record ToolEnvelope` — apply
     `@UtilityClass` on the outer class only; compile-verify in M3 (nested types are allowed).

     **Not** `@UtilityClass`: types with instance state or instance methods (e.g.
     `SourceRoutingPolicy`, `*McpResponseMapper`, adapters, use cases).

   Forbidden (conflicts with `AGENTS.md` §4, clean-java skill, record-heavy domain):

   - `@Data`, `@Setter`, `@AllArgsConstructor` on domain, application, or
     `@ConfigurationProperties` types.
   - Lombok on **records** (redundant).
   - `@Builder` + `@Setter` together, or any pattern that allows a partially-built invalid
     object to escape.

   Add `lombok.config` at repo root with `lombok.addLombokGeneratedAnnotation = true`.
   Configure `maven-compiler-plugin` `annotationProcessorPaths` for Lombok in parent
   `pluginManagement`; scope `provided` + `optional` on the Lombok dependency.

5. **`final var` for obvious locals** in touched code only (user micro-style). Do not
   reformat untouched methods solely for `var`.

6. **No magic empty strings** where they carry meaning. Replace `rawName == null ? "" :
   rawName.toString()`-style patterns with `StringUtils.defaultString(...)` or
   `trimToEmpty` + blank check — one named constant only when the empty string is a
   deliberate sentinel (rare; prefer typed absence).

7. **Documentation is part of the deliverable.** Update `docs/spec/10-code-quality-guidelines.md`
   §1 with Commons Lang + Lombok rules; mirror in `.claude/skills/clean-java/SKILL.md`.
   README: one line under Dependencies or Limitations noting the stack line (Boot 3.5.x, AI 1.1.x).
   **No ADR** — these choices are reversible convention, not architectural forks.

## Scope

- M1 dependency audit: run `./mvnw versions:display-property-updates` and
  `versions:display-dependency-updates` (add the versions plugin to parent `pom.xml` if
  missing); record before/after table in `docs/demo/implementation-notes.md` or a short
  `docs/plans/backend-hygiene-versions.md` appendix; apply approved bumps; full reactor
  `./mvnw test` green.
- Parent POM: `commons-lang3`, Lombok processor config, version property updates.
- `assistant-app` + `countries-mcp-server`: StringUtils/Objects pass on adapter + tool
  boundary code; `@Slf4j` on the five logger classes; `@UtilityClass` on the eight utility
  types listed above; `@RequiredArgsConstructor` on eligible Spring beans when touched;
  `@Builder` only where it materially shortens construction in touched code (no repo-wide
  retrofit).
- Spec + skill updates as above.

## Out of scope / non-goals

- Spring Boot 4, Spring AI 2, MCP SDK 2, Jackson 3 migration (separate platform plan).
- Streaming SSE (`docs/plans/stream-chat-answers-and-source-usage-trace.md`).
- Agentic orchestration (`docs/plans/improve-agentic-tool-orchestration.md`).
- Package restructure (`domain`/`infrastructure`, countries C-8 repackaging).
- Phase 8 README / AI usage report (separate plan).
- `chat-ui` changes.
- Checkstyle / Spotless / Error Prone introduction (nice follow-up; not this plan).
- Behaviour changes, routing changes, new APIs, new dependencies beyond Lang + Lombok.
- Replacing Java `record` value objects with Lombok classes.

## Invariants (must hold)

- `./mvnw test` and `./mvnw -pl e2e-tests verify -P e2e` (when stack is up) behave as before.
- No new `null` returns; no swallowed exceptions; hexagonal import boundaries unchanged
  (grep domain/application packages for Spring AI, MCP SDK, JDBC — still zero).
- Honesty, routing, and `/api/chat` JSON contract unchanged.
- `AGENTS.md` constructor-injection and complete-construction rules preserved (`@RequiredArgsConstructor`
  is constructor injection; `@Builder` products must be valid when built).

## Risks and open questions

- **Spring AI 1.1.x patch** may pull transitive changes — run full `assistant-app` tests +
  one live Ollama smoke (synthesis + embedding) after bump; rollback the AI version if
  incompatible.
- **Mechanical StringUtils sweep** can hide bugs if trim semantics change — each touched
  boundary keeps or gains a focused test; prefer editing files that already have tests.
- **Lombok in CI/reviewer IDE** — document in README that Lombok is required for compilation
  (standard for Java shops).

## Definition of Done

- [x] Version table recorded (before → after) for Boot, Spring AI, MCP SDK, jsoup; all bumps
      stay on the 3.5 / 1.1 / 1.0 lines unless a written exception is in the plan appendix.
      See `docs/plans/backend-hygiene-versions.md`.
- [x] `./mvnw test` BUILD SUCCESS with pasted reactor test totals (countries + assistant-app).
      See `docs/plans/backend-hygiene-versions.md` and `docs/demo/implementation-notes.md`.
- [x] `commons-lang3` declared in both Java modules; version managed by Spring Boot parent BOM
      (no parent `<dependencyManagement>` entry — deviation from original locked decision §2).
- [x] `grep -r "LoggerFactory.getLogger" assistant-app/src/main countries-mcp-server/src/main`
      returns **zero** matches (replaced by `@Slf4j`).
- [x] `grep -rE "@Data|@Setter|@AllArgsConstructor" assistant-app/src countries-mcp-server/src`
      returns **zero** matches.
- [x] The eight listed utility types carry `@UtilityClass`; no production utility type still
      uses a hand-written private empty constructor without `@UtilityClass` (grep
      `private \\w+\\(\\)` over those files → zero).
- [x] `lombok.config` at repo root with `lombok.addLombokGeneratedAnnotation = true`.
- [x] `StdioMcpToolInvoker` keeps explicit `@Autowired` constructor (not `@RequiredArgsConstructor`).
- [x] At least one `@RequiredArgsConstructor` Spring bean exists (`CountryLookupTool`,
      `CountriesMcpServerAdapter`); hand-written constructors remain only where the plan allows.
- **Deviation:** no justified `@Builder` site remains. `SuccessfulLookupFixture` was removed
  because it was a single-use test helper; see implementation notes.
- [x] Adapter/mapper null-blank sites identified in M2 use `StringUtils` (spot-check:
      `CountryLookupTool`, `CountriesMcpResponseMapper`, `WeatherMcpResponseMapper`,
      `RestCountriesHttpAdapter`, `StdioMcpToolInvoker`, `CountryFacts`, `LookupPlace` — each
      reviewed; list any intentional JDK-only sites in implementation notes).
- [x] `docs/spec/10-code-quality-guidelines.md` §1 documents Lang + Lombok policy; clean-java
      skill matches.
- [x] README mentions current stack versions (one short subsection or table).
- [x] Optional live smoke: one chat question + `--ingest-rag` dry run or documented skip with
      reason — skipped (no assistant on `localhost:8080`); see implementation notes.

## Milestones

- [x] **M1 — Dependency audit and bumps.** Add versions-maven-plugin to parent (if needed);
      bump Boot `3.5.9` → `3.5.12`, Spring AI → latest `1.1.x`, jsoup patch, MCP `1.0.x`
      patch if any; `./mvnw test` green; record version table.
      **Completed 2026-06-18 (uncommitted):** Boot `3.5.15` (metadata newer than plan baseline `3.5.12`),
      Spring AI `1.1.8`, MCP SDK `1.0.2`, jsoup `1.22.2` — see
      `docs/plans/backend-hygiene-versions.md`.
- [x] **M2 — Tooling: Commons Lang + Lombok wiring.** Parent dependencyManagement;
      `lombok.config`; compiler annotation processor; spec §1 + clean-java skill updated.
- [x] **M3 — Lombok ergonomics.** `@Slf4j` on five logger classes; `@UtilityClass` on the
      eight utility types; `@RequiredArgsConstructor` on eligible Spring beans touched in this
      milestone; `@Builder` only where it clearly helps in touched code; tests green.
- [x] **M4 — StringUtils / Objects boundary pass.** Touch adapter, MCP mapper, REST, and tool
      entrypoints listed in DoD; `final var` in edited methods; no domain-wide churn.
- [x] **M5 — Verification and notes.** Full reactor test paste; optional live smoke;
      implementation-notes entry summarizing what changed and what was explicitly deferred
      (Boot 4 / AI 2).

## Documentation impact

- `docs/spec/10-code-quality-guidelines.md` — §1 Java style: Commons Lang patterns; Lombok
  allowlist (`@Slf4j`, `@RequiredArgsConstructor`, `@Builder`, `@UtilityClass`) and denylist.
- `.claude/skills/clean-java/SKILL.md` — same rules, short checklist.
- `README.md` — dependency version summary (no secrets).
- `docs/demo/implementation-notes.md` — version bump log + any live smoke evidence.

## Follow-ups (separate plans, not this one)

- **Next: streaming** — `docs/plans/stream-chat-answers-and-source-usage-trace.md` + ADR
  `0009` (SSE `/api/chat`, live Source-Usage Trace). **Prerequisite:** this plan landed and
  green; implement streaming on the hygiened stack so Lombok/Commons conventions are already
  in place for touched adapter code.
- **Platform migration** — Spring Boot 4 + Spring AI 2 + MCP SDK 2 (needs ADR, upgrade-notes
  pass, full regression).
- **Agentic / package restructure / Phase 8** — unchanged from prior roadmap.
