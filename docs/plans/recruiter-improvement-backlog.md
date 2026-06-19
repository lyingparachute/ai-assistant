# Recruiter Improvement Backlog

This backlog captures bounded follow-up work that is credible for an interview discussion. It does not redefine the current recruitment scope; it gives concrete next steps after the documented local assistant demo.

## 1. Demo Readiness Panel

**Problem:** A reviewer can run the backend and Chat Interface, but live readiness is spread across terminal output, source badges, and demo evidence files. When a source is unavailable, the UI shows the answer honestly, but it does not proactively explain which local prerequisite is missing.

**Proposal:** Add a compact readiness panel in the Chat Interface that reads a lightweight backend health endpoint and shows backend, Ollama model, RAG store, countries source, and weather source status before the reviewer asks a demo question.

**Why now / why later:** This is useful if the project gets another polish pass before submission because it reduces reviewer confusion. It can wait because current command-line verification already exposes failures.

**Effort:** M

**Risk if skipped:** A reviewer may interpret an honest source-unavailable answer as a broken application instead of a missing local prerequisite.

**First verification step:** `cd chat-ui && npm test && npm run build`

## 2. Weather MCP Startup Diagnostics

**Problem:** The latest live demo capture shows the weather capability timing out while countries, RAG, and synthesis work. The assistant handles this honestly, but the failure appears only after asking a weather question.

**Proposal:** Add a startup or doctor check that validates `WEATHER_API_KEY`, `WEATHER_API_URL`, the MCP executable path, one controlled weather call, timeout behavior, and redacted diagnostics.

**Why now / why later:** This is high-value before a recruiter runs the weather demo. It should stay bounded to local diagnostics and not become a general observability platform.

**Effort:** S

**Risk if skipped:** The two required weather demo routes may be source-unavailable during review, which weakens the end-to-end story even though the fallback behavior is correct.

**First verification step:** `./scripts/capture-demo-answers.sh`

## 3. Bounded Agentic Orchestration Spike

**Problem:** Deterministic routing is appropriate for the fixed demo questions, but it does not scale well to new multi-source questions. ADR 0010 captures a proposed direction without claiming shipped behavior.

**Proposal:** Keep deterministic routing as the default and build an opt-in orchestration spike behind the same ports, with strict max steps, typed tool outcomes, cancellation, and trace evidence. Use `docs/plans/improve-agentic-tool-orchestration.md` as the deep plan rather than duplicating it here.

**Why now / why later:** This is a strong interview answer because it shows how to evolve the design without breaking the recruitment constraints. It belongs after submission unless the assignment explicitly expands beyond the six demo routes.

**Effort:** L

**Risk if skipped:** Future questions require more routing code and more tests per route.

**First verification step:** `./mvnw test`

## 4. Browser Smoke Test For Chat Interface

**Problem:** Chat Interface behavior is covered by unit tests and the API is covered by integration tests, but there is no browser-level smoke test that proves demo chips, streaming tokens, trace badges, final source metadata, and error rendering work together.

**Proposal:** Add one Playwright smoke test against a stubbed or locally running backend for the six demo chips and the SSE `final` event contract.

**Why now / why later:** This closes a visible demo risk. It can wait if the recruiter review is command-line focused, because existing tests already cover the controller and UI state logic separately.

**Effort:** M

**Risk if skipped:** A DOM or browser integration regression could pass unit tests and only appear during a live demo.

**First verification step:** `cd chat-ui && npm test && npm run build`

## 5. Demo Answer Quality Guard

**Problem:** The latest Berlin place answer is factually grounded but contains duplicated wording and malformed spacing around the population value. This is a presentation-quality issue, not a source-honesty failure.

**Proposal:** Add a focused formatting guard around country fact sentence composition and a regression test for the Berlin place route. Keep model wording flexible, but make deterministic facts render cleanly.

**Why now / why later:** This is small enough to fix before submission if time allows. It can wait if the weather blocker is higher priority, because the source trace remains correct.

**Effort:** S

**Risk if skipped:** A reviewer may notice rough answer polish even when the architecture and evidence are sound.

**First verification step:** `./mvnw -pl assistant-app test -Dtest=AnswerQuestionUseCaseTest`

## 6. Dependency And Local Secret Hardening

**Problem:** `npm audit --omit=dev` currently reports low-severity esbuild exposure through Astro, and local scripts depend on secrets being present without printing them. This is acceptable for a local demo, but it deserves explicit handling.

**Proposal:** Track the Astro/esbuild advisory, avoid forced downgrades, add a redacted environment check, and keep generated logs and captured payloads free of secret values.

**Why now / why later:** The local-only threat model makes this a hardening item, not a launch blocker. It is worth discussing because it shows discipline without overstating risk.

**Effort:** S

**Risk if skipped:** Security-conscious reviewers may ask why dependency and local secret checks are not visible.

**First verification step:** `cd chat-ui && npm audit --omit=dev`

## 7. One-Command Clean Checkout Doctor

**Problem:** Clean checkout verification currently lives in documentation and multiple scripts. The reviewer still needs to discover which prerequisite failed when Java, Node, Docker, Ollama, models, ports, or API keys are missing.

**Proposal:** Add `./scripts/doctor.sh` that checks runtime versions, port availability, Docker pgvector, required Ollama models, environment keys, and the weather/countries capabilities without starting the full assistant.

**Why now / why later:** This makes the repository easier to evaluate in under ten minutes. It can wait because the README and verification document already list the manual commands.

**Effort:** M

**Risk if skipped:** Setup failures remain diagnosable but slower to triage during a live interview.

**First verification step:** `./scripts/doctor.sh`
