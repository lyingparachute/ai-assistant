# ExecPlan — Phase 8 submission finalization (README, AI usage, verified commands)

Status: proposed — PLAN-READY pending approval (critic round-2 folded in)
Owner: TBD
Source: `docs/spec/06-implementation-plan.md` Phase 8 (lines 385–415);
`docs/spec/03-acceptance-criteria.md` README + AI usage (lines 66–76);
`docs/ai/README.md`; gap audit 2026-06-17.
Scope: `README.md`, `docs/ai/`, `docs/demo/` (verification artifact + optional evidence refresh).
**Documentation and verification only** — no production feature work, no new modules.

## Prerequisites

- **Phase 7 landed** — `docs/plans/phase-7-demo-capture.md` (demo evidence:
  `docs/demo/final-answers.md`, `demo-run-log.md`, `request-traces/`).
- **Upstream feature plans** — verify against whatever is landed at Phase 8 start:

  | Landed before Phase 8 | Phase 8 must |
  | --- | --- |
  | Baseline only (Phases 2–7 + refactors + ADR `0008`) | Use 2026-06-17 JSON-era demo evidence; README API → spec 14 as-is |
  | [`backend-hygiene`](backend-hygiene.md) | Tech stack table shows post-hygiene versions; add `docs/ai/backend-hygiene.md` |
  | [`stream-chat-answers-and-source-usage-trace`](stream-chat-answers-and-source-usage-trace.md) + ADR `0009` Accepted | **Blocking re-capture** for SSE; README API → updated spec 14; add `docs/ai/streaming-sse-api.md` |
  | [`improve-agentic-tool-orchestration`](improve-agentic-tool-orchestration.md) + ADR `0010` | README limitations document `assistant.orchestration.mode`; add `docs/ai/agentic-orchestration.md` |

- Phase 8 does **not** wait for agentic orchestration or Boot 4 migration to **start**.
- Phase 8 does **not finish** with stale JSON demo evidence if ADR `0009` is Accepted at completion
  (transport exit gate, design §7).

## Why now

Implementation and demo path are complete, but the **submission surface** is incomplete. A
recruiter opens `README.md` first. Verified gaps (2026-06-17):

| Artifact | State |
| --- | --- |
| `README.md` AI usage section | **Missing** (`docs/spec/03:68`) |
| `README.md` Limitations | **Stale** — `README.md:108` cites 2026-06-16 blockers; session 2 succeeded (`demo-run-log.md`) |
| `README.md` architecture / tech stack | Partial — Documentation table only; no stack version table |
| `docs/ai/` phase entries | Phases **1–3** only; phases 4–7 + refactors **missing** |
| Phases 1–3 Human Review | Still **"Pending"** in existing entries |
| Clean-checkout verification | **Not recorded** in a dedicated artifact |
| `docs/spec/07-test-strategy.md` | Not linked from README Documentation table |

Phase 8 makes the repo **reviewable and honest**: verified commands with pasted output, captured
evidence linked, limitations current, AI assistance disclosed.

## Target state

A reviewer can, in one sitting:

1. Read `README.md` — overview, architecture, **tech stack table**, prerequisites, setup, run,
   test, demo, **AI usage**, **limitations** — all accurate to landed code.
2. Follow README commands from a clean checkout (or read explicit documented blockers in
   limitations + `clean-checkout-verification.md`).
3. Open `docs/demo/final-answers.md` — evidence **captured from the running assistant**, not
   agent-authored answers.
4. Open `docs/ai/` — material AI-assisted work with human review + verification per entry.
5. See plainly what is out of scope (memory, agentic default, Boot 4).

## Design (locked decisions)

1. **Documentation-only phase.** No production Java/TypeScript feature changes unless a README
   command is objectively wrong (fix the command or document the blocker — never fabricate
   success).

2. **AI usage: phase-scoped entries, not one blob.**

   **Backfill (new files under `docs/ai/`):**

   | File | Covers |
   | --- | --- |
   | `phase-4-pgvector-rag-ingestion.md` | Phase 4 plan landed work |
   | `phase-5-assistant-orchestration.md` | Phase 5 |
   | `phase-6-chat-interface.md` | Phase 6 |
   | `phase-7-demo-capture.md` | Phase 7 |
   | `rest-countries-v5-migration.md` | ADR `0008` |
   | `refactor-batch-domain-and-rag.md` | Refactors 1–2 |
   | `refactor-batch-e2e-config-and-countries.md` | Refactors 3a, 3b, 4 |
   | `refactor-batch-http-and-chat-ui.md` | Refactors 5a, 5b |
   | `backend-hygiene.md` | If hygiene plan landed |
   | `streaming-sse-api.md` | If streaming plan landed |
   | `agentic-orchestration.md` | If agentic plan landed |

   **Close out (existing — Human Review still "Pending"):**

   - `phase-1-build-skeleton.md`, `phase-2-countries-mcp-server.md`,
     `phase-3-mcp-client-integrations.md` — replace "Pending" with real review notes **or**
     README limitation: "early AI entries not re-reviewed" (pick one; DoD requires no Pending).
   - `future-plan-critique.md` — mark superseded with pointers to landed refactor/exec plans.

   **Backfill method (M2):** For each file, run `git log --oneline -- <paths>` from the
   corresponding `docs/plans/*.md` Scope section; paste **existing** verification output from
   phase notes / `demo-run-log.md` / plan DoD — never invent in M3/M4.

3. **README AI usage = summary + pointer** (new `## AI Usage` section, 5–10 bullets):

   - AI assisted documentation and implementation per `docs/ai/`.
   - Demo answers and request traces were **captured from the running assistant** — link
     [`docs/demo/final-answers.md`](docs/demo/final-answers.md).
   - Human reviewed material changes; per-entry verification in `docs/ai/`.
   - Distinguish AI-assisted **authoring** from **runtime-captured** evidence.
   - **Source-unavailable honesty:** Atlantis in `final-answers.md` documents the routing-guard
     path; live weather-key-unset capture is **test-covered only**, not live-captured (per
     `final-answers.md` footnote) — README must not imply otherwise.

4. **README structure vs `docs/spec/03:68`.** Required sections and order:

   | Section | Content |
   | --- | --- |
   | Title + overview | One paragraph (existing opening line OK) |
   | Architecture | Short paragraph + link [`docs/spec/05-architecture.md`](docs/spec/05-architecture.md) |
   | Tech stack | Table: Java 21, Spring Boot, Spring AI, pgvector image, Ollama models, MCP — versions from root `pom.xml` / `application.yml` |
   | Prerequisites | Existing block + keys |
   | Quick Start / setup / RAG | Existing commands unchanged unless M3 proves wrong |
   | Tests | `./mvnw test`, `./mvnw verify`, `./mvnw verify -P e2e` |
   | Demo questions | Link `demo-questions.json`, `final-answers.md` |
   | AI usage | §3 bullets |
   | Limitations | §5 current state |
   | Documentation table | Add **`docs/spec/07-test-strategy.md`**; list ADRs `0001`–`0008` (+ `0009`/`0010` if Accepted) |

5. **Limitations = honest current state.** Replace `README.md:108` stale blockers with:

   - **Resolved (historical):** one line → [`docs/demo/demo-run-log.md`](docs/demo/demo-run-log.md)
     session 2 (2026-06-17). Keep session 1 detail in log only.
   - **Standing:** no cross-request memory (ADR `0006`); deterministic demo routing unless
     agentic landed and documented; local-only; no cloud LLM; RAG = CDQ product pages only.
   - **Not implemented:** agentic default, Boot 4, package restructure — link exec plans, do not
     imply they exist.

6. **Clean-checkout verification artifact.** Single file:
   **`docs/demo/clean-checkout-verification.md`**. `demo-run-log.md` gets a Phase 8 section
   linking here (no duplicate pastes). Record **actual pasted output** from this machine or
   document honest skip per row:

   | # | Command (README canonical) | Pass criterion |
   | --- | --- | --- |
   | 1 | `./mvnw test` | `BUILD SUCCESS` + test totals pasted |
   | 2 | `./mvnw verify` | `BUILD SUCCESS` |
   | 3 | `./mvnw -pl countries-mcp-server -am package -DskipTests` | jar path exists |
   | 4 | `cd chat-ui && npm install && npm run build` | build success (Node ≥ 22.12) |
   | 5 | `./scripts/start-assistant.sh` | stack starts **or** skip: name missing dep |
   | 6 | `./mvnw -pl assistant-app spring-boot:run -- --ingest-rag` | chunk count **or** skip |
   | 7 | `./mvnw verify -P e2e` | green **or** skip: no live stack on :8080 |
   | 8 | `scripts/capture-demo-answers.sh` | success **or** skip: same as row 7 |

   Skips must state: missing dependency, whether it blocks an acceptance criterion
   (`docs/spec/08-demo-plan.md`). Rows 1–4 must pass without external services.

7. **Demo evidence refresh rule (transport exit gate).**

   | ADR `0009` at Phase 8 completion | Action |
   | --- | --- |
   | Still Proposed | Confirm 2026-06-17 JSON evidence; README claims match `final-answers.md` |
   | **Accepted** | **Blocking** — re-run `capture-demo-answers.sh` + `./mvnw verify -P e2e` on SSE stack; update `final-answers.md`, `demo-run-log.md`, README API pointer |

   Phase 8 may start on JSON evidence; it may not finish with JSON evidence while SSE is live.

8. **No fabricated demo content.** Re-capture only from running assistant (`CLAUDE.md` §12).

## Scope

- `README.md` — all sections in design §4–§5; Documentation table ADR list.
- `docs/ai/` — backfill + closeout per §2 file map.
- `docs/demo/clean-checkout-verification.md` — new; link from `demo-run-log.md`.
- Optional evidence refresh per transport exit gate (§7).
- Cross-check `docs/spec/03:66-76` and spec `06:408-415` in M6.

## Out of scope / non-goals

- New features (agentic, Boot 4, hygiene, streaming implementation).
- Rewriting `docs/spec/` beyond contradiction fixes found during M6.
- Committing secrets, `.env`, or machine-specific paths.
- Implying e2e/demo success without pasted output or explicit skip.
- Editing `docs/adr/README.md` (template only — ADRs listed in README Documentation table).

## Invariants (must hold)

- Demo Answer Rule: no hand-written final answers or volatile weather values in docs.
- AI usage entries distinguish authoring from runtime-captured evidence.
- README commands verified with pasted output **or** listed as limitations with reason.
- README API transport matches `docs/spec/14-assistant-api-contract.md` at completion.
- Phases 1–3 AI entries: Human Review not "Pending" at Done.

## Risks and open questions

- Reviewer machine may lack Ollama/keys/Docker — rows 5–8 may skip; rows 1–4 must pass.
- AI backfill authorship uncertainty — use `git log`; mark honestly in Limitations field.
- Streaming lands mid-Phase-8 — M4 re-capture becomes mandatory before M5.

## Definition of Done

- [ ] `README.md` includes all `docs/spec/03:68` sections (overview, architecture, tech stack,
      prerequisites, setup, run, test, demo, AI usage, limitations).
- [ ] Stale `README.md:108` limitation text removed; session-2 success referenced.
- [ ] Tech stack table with versions from `pom.xml` (post-hygiene if landed).
- [ ] Documentation table links `docs/spec/07-test-strategy.md` and material ADRs.
- [ ] `docs/ai/` backfill complete per §2 table; each entry has verification evidence or skip.
- [ ] Phases 1–3 Human Review closed (no "Pending").
- [ ] `future-plan-critique.md` superseded or merged.
- [ ] README AI usage links `final-answers.md`; states runtime capture vs AI authoring.
- [ ] README honesty: Atlantis = routing-guard demo; weather-unavailable live = tests only.
- [ ] `docs/demo/clean-checkout-verification.md` — rows 1–8 pasted or honest skip each.
- [ ] `demo-run-log.md` Phase 8 section links clean-checkout artifact.
- [ ] Demo section states required + showcase outcomes honestly (success / skipped / blocked).
- [ ] If ADR `0009` Accepted at completion: demo re-captured for SSE; spec 14 + README aligned.
- [ ] Grep: no orphan `TODO`/`FIXME` in README or new `docs/ai/*` without tracked reference.
- [ ] No fabricated test output, demo answers, or trace content in updated files.
- [ ] Spec `06:408-415` acceptance criteria satisfied (mental tick + M6 note in demo-run-log).

## Milestones

- [ ] **M1 — Gap audit + README outline.** Run checklist:

  ```bash
  # README vs spec
  rg -n "AI [Uu]sage|Limitations|Architecture|tech stack" README.md
  # Missing ai entries
  ls docs/ai/
  # Stale limitations
  rg "2026-06-16" README.md
  ```

  Deliverable: `docs/demo/phase-8-gap-audit.md` (internal scratch, optional commit) or section
  in M2 commit message — lists missing `docs/ai/` files, README section gaps, ADR `0009` status
  at start. Draft README section outline (headings only).

- [ ] **M2 — AI usage backfill + closeout.** Create §2 backfill files from template; close
      phases 1–3; disposition `future-plan-critique.md`. Verification = paste from existing
      logs/plans only.

  ```bash
  git log --oneline -- assistant-app/   # phase 5 example
  git log --oneline -- docs/plans/phase-5-assistant-orchestration.md
  ```

- [ ] **M3 — Clean-checkout verification.** Run §6 table commands; paste to
      `docs/demo/clean-checkout-verification.md`. Date + git sha header in file.

- [ ] **M4 — Demo evidence confirm or refresh.** Transport exit gate (§7): if SSE live, re-capture;
      else confirm `final-answers.md` still valid. Add Phase 8 note to `demo-run-log.md`.

- [ ] **M5 — README finalize.** **Only after M3–M4.** Apply §3–§5 content; API pointer matches
      spec 14; paste diff summary in commit message.

- [ ] **M6 — Final cross-check + land.**

  ```bash
  rg "TODO|FIXME" README.md docs/ai/
  rg "2026-06-16" README.md   # must be empty in Limitations
  ```

  Tick `docs/spec/03:66-76` + `docs/spec/06:408-415`; set plan Status `landed — <date>`.

## Round-2 critic resolutions (authoritative)

- **[P8R2-1]** Milestone order: M3 verify + M4 demo **before** M5 README finalize.
- **[P8R2-2]** Close out phases 1–3 — no "Pending" Human Review at Done.
- **[P8R2-3]** ADRs in README Documentation table — not `docs/adr/README.md`.
- **[P8R2-4]** Clean-checkout rows 5–6: `start-assistant.sh` + RAG ingest (spec `06:398`).
- **[P8R2-5]** `npm install` (README path), not `npm ci`.
- **[P8R2-6]** E2E canonical: `./mvnw verify -P e2e`.
- **[P8R2-7]** ADR `0009` Accepted at completion → re-capture blocks Done.
- **[P8R2-8]** Tech stack table required regardless of hygiene status.
- **[P8R2-9]** Refactor AI file map locked in design §2 (three batch files).
- **[P8R2-10]** README honesty on source-unavailable live vs test-only paths.

## Documentation impact

- `README.md` — primary deliverable.
- `docs/ai/` — backfilled + closed-out entries.
- `docs/demo/clean-checkout-verification.md` — new verification artifact.
- `docs/demo/demo-run-log.md` — Phase 8 link section.

## Suggested commit message

`docs: finalize local assistant submission`

## Follow-ups (not Phase 8)

- Recruiter 5-minute demo script — optional README subsection.
- Agentic / Boot 4 — separate exec plans; limitations link only.
