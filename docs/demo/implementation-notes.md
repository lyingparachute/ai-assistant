# Implementation Notes — Phase 7 Demo Capture (+ countries unblock)

Running log of decisions, deviations, and tradeoffs made while driving Phase 7 to completion.
Started 2026-06-17.

## Context

Phase 7 (`docs/plans/phase-7-demo-capture.md`) was blocked. The 2026-06-16 capture hit three
upstream blockers (REST Countries v3.1 deprecated, `WEATHER_API_KEY` unset, RAG not ingested).
This session resolves what is resolvable and tracks what is not.

## Decisions (user-directed)

- **Countries v3.1 deprecation** → write a migration plan/ADR FIRST, no code until approved.
  Rationale: editing `countries-mcp-server` is outside Phase 7 scope (it is Phase 2 code), and the
  repo is documentation-first. A plan keeps code and docs from drifting.
- **Weather** → user provides `WEATHER_API_KEY` (WeatherAPI.com free tier). No fabrication.
- **Commits** → one meaningful commit per logical unit, matching existing `feat(...)/docs:` style.
  No per-milestone commit spam.

## Environment verified (2026-06-17)

- Ollama installed, `qwen3:4b` + `nomic-embed-text` present.
- Docker running, `assistant-pgvector` (pgvector/pgvector:pg17) up on 5432.
- `WEATHER_API_KEY` unset at session start.
- `countries-mcp-server` REST Countries base URL = `https://restcountries.com/v3.1` (deprecated).

## Progress

### 2026-06-17 — investigation + Phase 7 unblock prep

Live state verified against the running stack (port 8080):

| Demo question | Path | Status | Blocker |
| --- | --- | --- | --- |
| Germany capital | countries | UNAVAILABLE | REST Countries v3.1 dead |
| Munich weather | weather | blocked | `WEATHER_API_KEY` (now set) |
| Capital-of-Germany weather | combined | blocked | both |
| Berlin synthesis | place-synthesis | UNAVAILABLE | needs live country fact |
| CDQ Fraud Guard | cdq-product | **WORKS** (`rag_knowledge: USED`, real snippets) | none |
| Atlantis | unsupported (honesty guard) | returns no-route | by design |

- **RAG already ingested**: `rag_chunks` has 9 real CDQ rows (source
  `https://www.cdq.com/products/cdq-fraud-guard`). The 2026-06-16 "count 0" blocker
  is resolved; CDQ showcase verified live.
- **Weather key**: user supplied a WeatherAPI.com key; validated live (Munich 20.1°C,
  2026-06-17 09:15). Stored in gitignored `.env`.

#### Local secrets loading (added this session)

- Added committed `.env.example` (template, no secrets) and gitignored `.env` (real
  keys). Verified `git check-ignore .env` matches `.gitignore:9`.
- `scripts/start-assistant.sh` now sources `$REPO_ROOT/.env` (set -a) so keys reach
  the assistant JVM and, via `StdioMcpToolInvoker` weather passthrough, the weather
  subprocess. Rationale: gives the recruiter a documented way to run the live demo
  with their own free keys without any secret in the repo.

#### Countries v3.1 → v5 (planned, not implemented; user chose "plan first")

- v3.1 permanently dead. v5 verified: base `https://api.restcountries.com/countries/v5`,
  name lookup `/names.common/{value}`, capital `/capitals/{value}`, requires
  `Authorization: Bearer`, free tier 500 req/month.
- **Blocker found by critic + verified independently**: v5 marks `population` as
  "dynamic, no read, no search" — unfetchable. `CountryFacts` requires population, so
  v5 forces a domain decision (drop population vs switch provider). Population is woven
  across both modules, the MCP output, the HTTP response, and the answer text
  (`AnswerQuestionUseCase.java:272`). Decision raised to user. ADR `0008` + plan
  `docs/plans/fix-countries-rest-v5-migration.md` capture options; recommend dropping
  population for a recruitment-scope demo.
- Plan critic review (fresh subagent) caught: wrong v5 endpoint paths, an
  unverified "confirmed" success-body claim, a false "mirror weather passthrough"
  claim (passthrough is weather-only/hardcoded), and an `@NotBlank`-vs-graceful-
  failure contradiction. All corrected in the plan/ADR before commit.

#### Still blocked / pending

- Country-fact demos: pending the population decision, then v5 implementation
  (needs a REST Countries v5 key).
- Full live demo capture: pending countries fix + a stack restart with `.env` loaded
  (current 8080 process started without the weather key).
