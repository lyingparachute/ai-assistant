# ExecPlan — Chat UI Playwright smoke tests

Status: **PLAN-READY** — round-1 critic folded in
Owner: TBD
Source: follow-up from [`improve-chat-interface.md`](improve-chat-interface.md) §Follow-ups;
vitest + happy-dom cover unit/controller paths but not a real browser against the Astro dev server.
Scope module: `chat-ui/` (Playwright config, smoke specs, npm scripts). Optional CI wiring in root
`pom.xml` or a documented manual gate — **no** changes to Assistant API contract.

## Prerequisites

- [`improve-chat-interface.md`](improve-chat-interface.md) **M1 + M2a landed** — session display,
  `chatController`, Stop/`AbortSignal`, error turn states, demo chips, thread layout + scroll
  container (M2a). Parent M2b–M5 may still be open; this plan does not wait on them.
- `cd chat-ui && npm run test && npm run build` green before adding Playwright.
- Node `>=22.12.0` (existing `chat-ui/package.json` engines).
- **Suggested execution order (recruitment):** after
  [`sse-trace-agentic-turn-index.md`](sse-trace-agentic-turn-index.md) and
  [`backend-sse-disconnect-cancellation.md`](backend-sse-disconnect-cancellation.md); before optional
  [`chat-ui-localstorage-session-display.md`](chat-ui-localstorage-session-display.md).

## Why now

Round-1 Chat Interface testing deliberately stayed in vitest + happy-dom (`improve-chat-interface.md`
§12: “No Playwright in v1”). That was the right YAGNI cut for the first UX slice, but it leaves
gaps a DOM adapter cannot catch:

| Gap | Risk |
| --- | --- |
| Astro bootstrap + inline module graph | Import/build errors that vitest never loads |
| Real `fetch` + SSE against a live page | Abort/Stop wiring, CORS, env `PUBLIC_ASSISTANT_API_URL` |
| Keyboard / focus / scroll in a browser | a11y AC from §9 verified only manually today |
| Sticky composer + thread layout | CSS regressions invisible to node tests |

A **small** Playwright smoke suite closes the highest-value hole — “does the Chat Interface load and
behave as a browser app?” — without duplicating the six-question e2e demo matrix (that stays in
`e2e-tests/` against the API).

## Target state

A contributor runs one command and gets a fast browser smoke against `chat-ui` dev server:

1. Page loads at `http://localhost:4321` with thread scaffold, composer, demo chips visible.
2. Submitting a question (mocked API **or** stubbed route) appends a user turn and shows an
   assistant turn region with `role="log"` / `aria-live` intact.
3. Stop mid-stream (mocked slow SSE) marks turn `aborted` with partial content retained.
4. Optional: one keyboard AC — Enter submits, Shift+Enter inserts newline.

**Default strategy (locked):** mock `/api/chat` at the browser layer (`page.route`) so smoke does
**not** require Ollama, pgvector, or `./scripts/start-assistant.sh`. Keeps CI/recruitment scope
realistic. One optional tagged spec (`@live`) documents full-stack manual run against a running
assistant.

## Scope

- Add `@playwright/test` dev dependency + `playwright.config.ts` (Chromium only for v1).
- npm scripts: `test:smoke` (headless), `test:smoke:ui` (optional debug).
- Smoke specs under **`chat-ui/e2e/`** (locked in M0 — no alternate path).
- Mock SSE fixture helper reusing shapes from `chat-ui/src/lib/types.ts` and existing vitest SSE
  parser tests — no second protocol definition.
- Document run instructions in `README.md` and a short note in `docs/spec/07-test-strategy.md`.
- Link from `improve-chat-interface.md` follow-ups as the scoped Playwright plan.

## Out of scope / non-goals

- Replacing `e2e-tests/` API verification or `./mvnw -pl e2e-tests verify -P e2e`.
- Full demo-question matrix in Playwright (six captures stay in Java e2e + capture script).
- Cross-browser matrix (Firefox, WebKit), visual regression, or Percy-style screenshots.
- Playwright Component Testing or migrating vitest tests to Playwright.
- CI mandate in Phase 8 unless a maintainer explicitly opts in — document local smoke as the
  minimum bar; optional GitHub Actions job is a follow-up.
- Backend or SSE protocol changes.
- `astro preview` / production build smoke — YAGNI; `astro dev` matches local reviewer workflow.

## Invariants (must hold)

- Smoke tests use **mocked** API responses by default — no invented live assistant answers.
- Mock payloads match ADR `0009` event shapes (`trace`, `token`, `final`, `error`).
- Vocabulary unchanged: **Chat Interface**, **session display**, **Source-Usage Trace** (`CONTEXT.md`).
- Existing `npm run test` (vitest) remains the fast inner loop; Playwright is additive.

## Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Flaky dev-server timing | `webServer` block in Playwright config waits on port 4321 |
| Duplicated SSE fixtures | Share JSON fixtures with vitest via `chat-ui/test-fixtures/` |
| Scope creep into full e2e | Cap at ≤5 smoke specs; no demo JSON assertion parity with Java e2e |
| Astro HMR vs preview drift | Smoke runs against `astro dev` (matches local reviewer workflow) |

## Definition of Done

- [ ] M0: Playwright installs; `webServer` starts `npm run dev`; one “page loads” spec green.
- [ ] Spec directory locked to `chat-ui/e2e/` — `grep` shows no alternate `tests/e2e/` path.
- [ ] Mocked SSE: submit question → user + assistant turns visible; trace timeline renders.
- [ ] Mocked `page.route('**/api/chat')` payloads **byte-match** shared fixture file used by vitest SSE
  tests (same JSON file path in both suites).
- [ ] Mocked slow stream + Stop → visible “Stopped — incomplete” (or locked label from
  `chatController`) **and** `#message-thread` non-empty.
- [ ] Keyboard: Enter sends; Shift+Enter inserts newline — one spec **or** documented skip citing
  parent §9 items 9–10 with reason.
- [ ] Scroll/focus: one spec for pinned scroll during streaming **or** documented skip citing parent
  §9 item 13; focus-after-final optional (§9 item 10).
- [ ] ≤5 spec files under `chat-ui/e2e/` — `find chat-ui/e2e -name '*.spec.ts' | wc -l` ≤ 5.
- [ ] `cd chat-ui && npm run test:smoke` — paste pass output in plan land note.
- [ ] README section: when to run smoke vs vitest vs Java e2e.
- [ ] `docs/spec/07-test-strategy.md` updated with Chat Interface browser smoke tier.
- [ ] Optional `@live` spec documented for full-stack manual run (not required in CI v1).
- [ ] Follow-up note: extend smoke with localStorage banner AC after
  [`chat-ui-localstorage-session-display.md`](chat-ui-localstorage-session-display.md) lands (optional
  plan).

## Milestones

### M0 — Tooling gate (stop if red)

1. Add Playwright + config with `baseURL` `http://localhost:4321`.
2. `webServer: { command: 'npm run dev', port: 4321, reuseExistingServer: !process.env.CI }`.
3. Create **`chat-ui/e2e/`** directory (locked path — no `tests/e2e/` alternate).
4. One spec: navigate to `/`, assert `#question`, `#message-thread`, demo chips present.

**Verify:** `cd chat-ui && npx playwright install chromium && npm run test:smoke` — paste output;
`test ! -d chat-ui/tests/e2e` — no alternate path.
**Stop if dev server fails to bind or spec cannot find scaffold IDs.**

### M1 — Mocked happy path

- `page.route('**/api/chat', …)` returns scripted SSE (`trace` + `final` for Germany-shaped question).
- Assert thread gains user message, assistant answer `textContent`, source cards after `final`.
- Reuse fixture JSON colocated with vitest SSE tests.

**Verify:** paste smoke output; no live backend required.

### M2 — Abort + error paths

- Slow mocked stream (delayed `final`); click Stop; assert aborted label + partial trace retained.
- Mock SSE `error` mid-stream; assert stream-error turn state (not blank workspace).

**Verify:** paste smoke output.

### M3 — Docs + land

- README + test strategy updates.
- Optional `@live` spec behind env flag (`RUN_LIVE_SMOKE=1`) documented in manual checklist.
- Mark plan **landed — `<sha>`**.

## Documentation impact

- `README.md` — Playwright smoke commands, mock vs live.
- `docs/spec/07-test-strategy.md` — three-tier testing: vitest → Playwright smoke → Java e2e.
- `docs/ai/chat-interface-improvements.md` — note browser smoke on completion.

## Round-1 critic resolutions (authoritative; supersede the body where in conflict)

- **[P1R1-1]** Prerequisites locked to parent **M1 + M2a** (not vague “M1+”); M2b–M5 not required.
- **[P1R1-2]** DoD adds binary fixture byte-match AC; Stop spec asserts label + non-empty thread.
- **[P1R1-3]** Spec path locked to `chat-ui/e2e/` in M0; grep-no-alternate in DoD.
- **[P1R1-4]** Keyboard AC (Enter/Shift+Enter) or documented skip tied to parent §9 items 9–10.
- **[P1R1-5]** Scroll AC (pinned scroll) or documented skip tied to parent §9 item 13.
- **[P1R1-6]** ≤5 spec files enforced in DoD.
- **[P1R1-7]** Cross-link: extend smoke for localStorage banner after optional localStorage plan lands.
- **[P1R1-8]** `astro preview` explicitly out of scope (YAGNI).
- **[P1R1-9]** Recruitment execution order: after sse-trace + backend-sse; before optional localStorage.
