# ExecPlan — Opt-in localStorage session display

Status: **PLAN-READY — optional / post-submission** — round-1 critic folded in; non-blocking for
Phase 8 demo acceptance
Owner: TBD
Source: follow-up from [`improve-chat-interface.md`](improve-chat-interface.md) §Follow-ups and §1
(v1 explicitly excluded `localStorage`); ADR `0006` conversation memory out of scope.
Scope module: `chat-ui/` only. **No** Assistant API changes — requests remain `{ question }` only.

## Prerequisites

- [`improve-chat-interface.md`](improve-chat-interface.md) **M2a+ landed** — in-memory session
  display, `sessionDisplay.ts`, `chatController`, thread UI vocabulary locked.
- ADR [`0006-keep-conversation-memory-out-of-scope.md`](../adr/0006-keep-conversation-memory-out-of-scope.md)
  **Accepted** — this plan must not introduce assistant memory or cross-request API history.
- **Recruitment priority:** reviewer convenience only; parent
  [`improve-chat-interface.md`](improve-chat-interface.md) §Out of scope excluded `localStorage` in v1;
  `docs/spec/03-acceptance-criteria.md` “Refresh clears the display” remains true when opt-in is off.

## Why now

v1 session display clears on refresh (`improve-chat-interface.md` §1: in-memory only). Reviewers
re-running demo questions lose visible thread context when reloading the page — friction during
local evaluation, not a product requirement.

Persisting the **Chat Interface session display** locally is safe **only** if:

1. It is **opt-in** with explicit consent UI.
2. A persistent **banner** states content is **not sent to the assistant** (ADR `0006`).
3. Stored data is **display state only** — never `{ question }` history batching, never prompt
   injection into API calls.
4. Vocabulary stays **session display**, not “memory” or “context” (`CONTEXT.md` Avoid lists).

This follow-up adds reviewer convenience without crossing the memory boundary.

## Target state

1. First visit: default **off** — same as today (in-memory only).
2. Settings/control: “Keep session display on this device” toggle (opt-in).
3. When enabled: turns serialize to `localStorage` under a namespaced key
   (e.g. `localassistant.chat.sessionDisplay.v1`); restore on load.
4. **Banner** (always visible while persistence enabled):

   > Session display is saved on this device only. Prior turns are **not sent** to the assistant.
   > Each question is still answered on its own.

5. “Clear display” action wipes in-memory + stored copy.
6. **Disable toggle clears storage** (locked) — keys absent, banner hidden; not “stop writes only”.

Refresh with opt-in on restores visible thread; API payloads unchanged.

## Design (locked decisions)

### 1. ADR `0006` compliance

| Allowed | Forbidden |
| --- | --- |
| Client-side display JSON (question text, status, trace steps, final snapshot for UI) | Server-side memory tables |
| Opt-in local persistence of display | `question` field becoming an array or history blob on API |
| Banner + README honesty | Calling stored turns “memory” in UI copy |

Amend `docs/spec/03-acceptance-criteria.md` with wording: **“Optional Chat Interface enhancement;
not required for demo acceptance.”** Persistence is display-only, not assistant capability.

### 2. Storage schema (versioned)

```text
{
  "version": 1,
  "turns": [ { /* SessionTurn snapshot — no secrets */ } ],
  "savedAt": "ISO-8601"
}
```

- Cap stored turns (default **50**) to avoid unbounded growth.
- **Persist only terminal turns:** `complete`, `aborted` (partial snapshot + `abortReason`), and
  terminal `stream-error` turns. Drop in-flight `streaming` turns on save and on `beforeunload`
  (locked — no partial-stream persistence).
- No API keys, env URLs, or PII beyond what the user typed as questions.

### 3. Controller integration

- `sessionDisplay.ts` gains optional `StorageAdapter` interface (`load` / `save` / `clear`).
- `localStorageAdapter.ts` implements adapter; `chatController` debounces save (e.g. 300 ms) on turn
  state changes when opt-in enabled.
- Pure tests mock adapter — no real `localStorage` in node tests (use in-memory fake).

### 4. UX

- Toggle in header or footer — labelled “Save session display on this device”.
- Banner: `role="status"`, dismiss **not** allowed while enabled (only hide via disable).
- Clear button copy: “Clear session display” (not “clear memory”).
- **Enable ordering:** banner visible **before** first debounced save (not after first write).

### 5. Preference key

- Separate preference flag (e.g. `localassistant.chat.sessionDisplay.enabled`) from turn blob.
- Corrupt or missing preference with valid blob → treat as disabled; empty display; no throw.
- Enabled flag without v1 blob → empty display; no throw.

### 6. Security

- Stored question/answer text is untrusted on reload — render through existing trust boundaries
  (`textContent` for answers; `htmlEscape` for trace/sources).
- No `eval`, no `innerHTML` for stored answer text.

## Scope

- `StorageAdapter` + `localStorageAdapter.ts` + versioned schema.
- Opt-in toggle UI + persistent banner component/markup in `index.astro` shell.
- `chatController` hooks: load on bootstrap, save on turn transitions, clear on user action.
- Debounce + turn cap; handle quota exceeded (`QuotaExceededError`) with user-visible message.
- vitest: adapter round-trip, opt-in gating (off = no writes), schema version reject.
- README + spec `03` + `docs/ai/chat-interface-improvements.md` update.
- Supersede `improve-chat-interface.md` §1 “Do not use localStorage in v1” with link to this plan.

## Out of scope / non-goals

- `sessionStorage` or IndexedDB (YAGNI unless quota forces — document in follow-up).
- Cross-tab sync, cloud backup, export/import.
- Sending stored history to `/api/chat` or any future multi-turn API.
- Encrypting localStorage (local-only demo scope).
- Persisting user preferences beyond this toggle (theme, etc.).
- Assistant-side persistence (ADR `0006`).

## Invariants (must hold)

- API request body: **`{ question }` only** — single string, current turn.
- Opt-in default **off** on first visit.
- Banner visible whenever persistence enabled.
- Vocabulary: **session display**; never “memory”, “transcript”, or “context” as product terms.
- Reload restore does not auto-submit questions or trigger API calls.

## Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Reviewer assumes assistant remembers | Banner + README + toggle label |
| Stale schema after UI refactor | `version` field; migrate or clear |
| Quota exceeded | Cap turns; catch error; disable save with message |
| XSS via stored payload | Same render paths as live SSE data |

## Definition of Done

- [ ] M0: schema + fake adapter tests green.
- [ ] Opt-in off by default; enabling shows banner **before** first debounced save.
- [ ] Enable → two turns → refresh → both visible; API still single-question (network tab or stub:
  request body `{ question: string }` only — no history field).
- [ ] Disable (locked: clear on disable) → `localStorage` keys absent + banner hidden.
- [ ] “Clear session display” wipes UI + storage.
- [ ] Turn cap enforced; only terminal turns persisted (`complete`, `aborted`, `stream-error`);
  `streaming` dropped on save and `beforeunload`.
- [ ] Quota error handled without silent failure.
- [ ] vitest: opt-in gating (off = no writes), schema round-trip, corrupted JSON → empty display,
  enabled flag without blob → empty display, corrupt preference → disabled + empty display.
- [ ] `cd chat-ui && npm run test && npm run build` — paste totals.
- [ ] README + spec `03` updated; ADR `0006` compliance note added (no new ADR required if
  boundaries documented in spec).

## Milestones

### M0 — Schema + adapter (stop if red)

1. Define versioned JSON schema + `StorageAdapter` interface.
2. `localStorageAdapter` with namespaced key.
3. vitest with in-memory fake: save/load/clear round-trip.

**Verify:** paste vitest output. **Stop if schema cannot represent existing `SessionTurn` type
without API fields.**

### M1 — Opt-in UX + banner

- Toggle UI wired to preference flag (stored separately: `…sessionDisplay.enabled`).
- Banner markup + CSS; shown iff enabled — **before** first debounced save.
- Default off on first visit.
- Disable → clear both preference and turn blob keys (locked).

**Verify:** manual: enable → banner visible immediately; disable → banner gone + keys absent.

### M2 — Controller persistence

- Load on bootstrap when enabled; debounced save on turn complete/error/abort.
- **Drop in-flight `streaming` turn** on `beforeunload` save (locked — omit partial turn).
- Clear action.

**Verify:** vitest controller tests with fake adapter; manual refresh restore.

### M3 — Docs + land

- README, spec `03`, chat-interface improvements doc.
- Mark plan **landed — `<sha>`**.

## Documentation impact

- `README.md` — opt-in persistence, banner meaning, clear/disable behaviour.
- `docs/spec/03-acceptance-criteria.md` — optional client persistence bullet (display only; not
  required for demo acceptance).
- `docs/ai/chat-interface-improvements.md` — supersede v1 non-persistence note.
- `improve-chat-interface.md` — link follow-up as authoritative for localStorage.

## Round-1 critic resolutions (authoritative; supersede the body where in conflict)

- **[L3R1-1]** Status: **optional / post-submission** — non-blocking for Phase 8 demo acceptance.
- **[L3R1-2]** Disable → **clear storage** (locked); not “stop writes only”.
- **[L3R1-3]** `beforeunload` / save: **omit in-flight `streaming` turns** (locked).
- **[L3R1-4]** Schema: persist only `complete`, `aborted` (+ partial + `abortReason`), terminal
  `stream-error`; drop `streaming`.
- **[L3R1-5]** Preference key AC: corrupt/missing `enabled` flag → disabled, empty display, no throw.
- **[L3R1-6]** spec `03` wording: “Optional Chat Interface enhancement; not required for demo acceptance.”
- **[L3R1-7]** Banner visible **before** first debounced save (ordering AC).
- **[L3R1-8]** Network/stub AC: restored session + new question → `{ question: string }` only.
