Status: landed — 2026-06-17 (demo captured live from a fully grounded stack; see docs/demo/final-answers.md)

# Phase 7: Tests and Demo Answers

## Prerequisites

- Phase 6 landed: HTTP chat API + Chat Interface callable locally.
- Phase 5 structured request traces available for capture.

## Scope

- `e2e-tests/` black-box checks against running `assistant-app` HTTP chat API (routing/trace fields, not volatile weather values).
- Live demo capture: four required questions + one CDQ Fraud Guard showcase question + one source-unavailable scenario.
- `docs/demo/final-answers.md`, `docs/demo/demo-run-log.md`, `docs/demo/request-traces/` with honest evidence (no invented answers).
- Record focused test commands and actual output in demo run log.

## Out of scope

- New orchestration features.
- Committing this plan file.

## Definition of Done

- [x] Required demo answers captured only from running assistant.
- [x] CDQ showcase question captured with RAG + synthesis source path evidence.
- [x] One source-unavailable demo captured with matching trace.
- [x] `docs/demo/request-traces/` has one excerpt per required question.
- [x] E2E module tests pass against local stack or document explicit blockers.

## Milestones

- [x] M1 — `e2e-tests` module scaffold + HTTP client checks
- [x] M2 — Live demo capture session + demo docs
- [x] M3 — Verification, README demo links, implementation-notes
