# ExecPlan — e2e demo verification honesty gating

Status: landed — 39b65df
Owner: TBD
Source: docs/reviews/2026-06-16-code-quality-audit.md (E-1, B-2, X-M-1)
Scope: `e2e-tests` module + `pom.xml` (reactor) + `scripts/capture-demo-answers.sh` + a shared demo-question
resource. Independent of the config refactor (refactor-3b). Ship this first — it is the honesty fix.

## Why now

`RequiredDemoQuestionsE2ETest` is surefire-bound (`*Test`) in a default reactor module, so it runs on every
`mvn test`. When no assistant server is up it self-skips via `assumeTrue(client != null)` (`@BeforeAll` line 25,
plus a redundant guard at line 101) and reports **green** while asserting nothing — the build presents demo
verification as passing without a real run, violating AGENTS §8/§10 (no fabricated runtime evidence). (Verified:
the captured run showed 5 tests, 0 skipped, 60.7s — i.e. it only "passes" when a server happens to be up.)

## Problem (verified against source)

1. **Green-skip masquerade (E-1, B-2)**: surefire binding + `assumeTrue` ⇒ no-server runs are green-skips.
   `mvn test` couples to runtime state.
2. **Removing the skip is the actual fix** [R1: P3-3]: renaming to `*IT`/failsafe or profile-gating changes
   *when/whether* it runs, but `assumeTrue` would still skip-green when invoked without a server. The skip
   guards must be removed so the opt-in invocation **fails** (not skips) when no server is up.
3. **Demo question set drift (X-M-1)** [R1: P3-2]: `capture-demo-answers.sh` has **6** prompts (incl. the
   CDQ-product question and the source-unavailable "Atlantis" case); `RequiredDemoQuestionsE2ETest` (lines 14-18)
   has **4**. They are not a clean duplicate.

## Target state (decisions locked)

- **Opt-in, fail-not-skip**: convert `RequiredDemoQuestionsE2ETest` to a failsafe `*IT` AND profile-gate the
  `e2e-tests` module so `mvn test` never runs it. **Remove both `assumeTrue(client != null)` guards** [R1: P3-3];
  the `@BeforeAll` connect step throws a clear failure if no server responds, so the deliberate command
  (`mvn verify -P e2e` or equivalent — name it in the plan and README) **fails** when no server is up. Document
  the deliberate invocation.
- **Single authoritative demo-question set** [R1: P3-2]: create one resource file (e.g.
  `e2e-tests/src/test/resources/demo-questions.json` or a shared properties file) holding the **superset of 6**
  questions, each keyed/tagged with its expected source path (country/weather/combined/place-synthesis/
  cdq-product/source-unavailable). The `capture-demo-answers.sh` reads all 6; the IT reads the file and runs the
  subset it asserts on, selected by key. **Decision**: the IT keeps asserting the same questions it asserts today
  (no silent coverage expansion — honors the non-goal); the shared file is the single source, the IT subsets it
  by key. If the team later wants the IT to cover all 6, that is a separate change with new assertions.

## Non-goals

- No change to what the demo verifies or to demo answers (captured only from a real run, AGENTS §12). The IT's
  asserted question set and assertions are unchanged; only their source (a shared file) changes.
- No config/records/build-version work (that is refactor-3b).

## Invariants to preserve

- `mvn test` (no server) stays green and hermetic AND does not execute the demo IT at all.
- The opt-in demo command FAILS (non-zero, visible) when no server is up — never green-skips.
- No fabricated runtime evidence; demo answers still come only from a real captured run.

## Risks / open questions

- Profile-gating the module must keep it reachable in the deliberate command; document the exact invocation in
  README + docs/spec/08-demo-plan.md.
- Failsafe `*IT` requires the `maven-failsafe-plugin` bound in the e2e module; verify it is present or add it.

## Definition of Done (binary)

- [x] `mvn test` (no running server) does **not** execute `RequiredDemoQuestions*` (grep surefire report / module
      not in the default reactor test phase).
- [x] `grep -rn "assumeTrue" e2e-tests/src` is empty; running the demo IT with no server up **fails** (non-zero
      exit), proven by an actual no-server run captured in the PR.
- [x] Demo questions exist in exactly one file; `capture-demo-answers.sh` and the IT both read it; `grep` shows
      no inline question-string literals duplicated across the two.
- [x] The deliberate demo-verification command is documented in README + docs/spec/08.
- [x] `./mvnw -o test` BUILD SUCCESS and hermetic.

## Round-2 critic resolutions (authoritative)

- **[R2-3a-1, blocking] README is a THIRD copy of the demo questions** (README §Tests lists 5, with CDQ wording
  "What does CDQ Fraud Guard do?" vs the capture script's "What is CDQ Fraud Guard?"). Add README to the
  deduplication: README references the shared question file / docs rather than inlining a divergent list, and the
  CDQ wording is reconciled to one canonical string in the superset. DoD bullet 3 is amended to "no inline demo
  question-string literals remain in the capture script, the IT, OR README."
- **[R2-3a-2, major] README command replaced, not appended.** `./mvnw -pl e2e-tests test` (README §Tests) stops
  running the demo once it is failsafe-bound; that stale command must be REPLACED with the new opt-in
  `mvn verify -P e2e` (or chosen) invocation, and a grep asserts the old command is gone or annotated unit-only.
- **[R2-3a-3 + R2-3a-4, major] Single gating mechanism locked.** Use **failsafe `*IT` + a profile `-P e2e` that
  gates failsafe *execution*** — the module stays compiled in the default reactor; `mvn test` runs no IT (surefire
  ignores `*IT`); `mvn verify` WITHOUT `-P e2e` runs no IT either. `maven-failsafe-plugin` is confirmed ABSENT
  from `e2e-tests/pom.xml` and must be added. "Profile-gate the whole module" is dropped to avoid the
  compile-exclusion ambiguity. DoD adds: `mvn verify` (no profile) does not execute the IT.
