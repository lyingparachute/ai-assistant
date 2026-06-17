Status: landed — 393dcb3

# Phase 6: Chat Interface

## Prerequisites

- Phase 5 landed (`6223a2d` lineage): `AnswerQuestionUseCase`, `ResponseComposer`, deterministic routing.
- No orchestration changes unless required to expose existing `ConversationTurn` / `traceCorrelationId` through HTTP DTOs.

## UI stack decision (locked)

- **No Thymeleaf.** No server-rendered pages inside `assistant-app`.
- **Separate frontend module** `chat-ui/` at repository root, built with **Astro**.
- `assistant-app` is **API-only** (JSON REST). The Chat Interface is a browser client that calls the Assistant API over HTTP.
- Local dev: two processes — Spring Boot backend + Astro dev server (`npm run dev`, default `http://localhost:4321`).
- CORS on the backend allows the documented Astro dev origin only (configurable, not hardcoded in business logic).

## Scope

### Backend (`assistant-app`)

- `docs/spec/14-assistant-api-contract.md` — lock HTTP contract before code.
- `adapters/inbound/http`: thin `ChatController`, `ChatRequest`, `ChatResponse` mapping `UserQuestion` ↔ `ConversationTurn` / `AssistantAnswer`.
- `spring-boot-starter-web`; **`POST /api/chat` JSON API only** (no `GET /` HTML from Spring).
- `assistant.cors.*` configuration for allowed frontend origins.
- Single-turn only: request carries current question text only (no history, session, or prior turns — ADR `0006`).
- HTTP status policy: validation failures → `400`; orchestration outcomes (including source-unavailable answers) → `200` with structured body; unexpected failures → `5xx` without stack traces.
- `@ControllerAdvice` for validation and unexpected errors.
- Contract tests pin JSON field shapes; HTTP integration tests (happy, validation, source-unavailable) with controlled `AnswerQuestionUseCase` test double.

### Frontend (`chat-ui/`)

- New Astro project (minimal: one page, no router, no client-side chat history).
- Single-turn form: question input → `fetch` `POST /api/chat` → render `answerText` + structured `sources[]`.
- Display per-source attribution (countries, weather, RAG, model synthesis) and source-unavailable states from API JSON — **no second formatting layer** that re-derives facts.
- Weather display: location, temperature, timestamp labeled Observed or Retrieved when present in `sources[]` / `answerText`.
- API base URL from environment (e.g. `PUBLIC_ASSISTANT_API_URL`, default `http://localhost:8080` for local dev).
- No localStorage/session history, no multi-turn scrollback (ADR `0006`).

### Documentation

- README: start backend, start `chat-ui`, open Astro URL, note Phase 7 for demo capture.

## Out of scope

- Demo answer capture (`docs/demo/*`) — Phase 7.
- `e2e-tests/` black-box module expansion — Phase 7 (Phase 6 may document manual two-process smoke).
- Server-side chat history; client-side turn history or session persistence.
- Streaming, WebSockets, SSE, rich chat UX beyond single Q→A.
- Orchestration routing changes, port adapter changes.
- Thymeleaf, Freemarker, or any HTML rendering inside `assistant-app`.
- Committing this plan file.

## Locked API contract (summary — full detail in spec/14)

- `POST /api/chat` — body `{ "question": "<non-empty string>" }`
- `200` response `{ "answerText", "sources": [...], "traceCorrelationId" }` with discriminated source entries matching `AnswerSource` variants
- `400` for blank/missing question
- CORS preflight supported for browser `fetch` from `chat-ui` dev origin

## Definition of Done

- [x] `docs/spec/14-assistant-api-contract.md` exists and matches implementation.
- [x] `./mvnw -pl assistant-app test` passes; output recorded in `implementation-notes.md`.
- [ ] Reviewer starts `assistant-app` and `chat-ui` via documented commands and opens the Astro dev URL.
- [x] Single-turn: no server-side history; API request schema has no history fields; UI does not persist prior turns.
- [x] Successful answers show per-source attribution in the UI from `sources[]`.
- [x] Source-unavailable answers name the failed source in the UI; no fabricated facts.
- [x] Weather answers show location, temperature, timestamp labeled Observed or Retrieved.
- [x] Berlin-style answers distinguish countries facts from model synthesis in `sources[]` display.
- [x] `traceCorrelationId` returned in API response (optional display in UI).
- [x] Controller delegates only to `AnswerQuestionUseCase`; no port injection.
- [x] Contract tests + HTTP integration tests: happy path, validation `400`, source-unavailable `200`.
- [x] README documents backend port, Astro URL, env var for API base URL, and two-process startup.

## Milestones

- [x] M1 — spec/14, backend web + CORS, DTOs, `ChatController`, validation, `@ControllerAdvice`
- [x] M2 — `chat-ui/` Astro scaffold, chat page, `fetch` to `/api/chat`, source attribution UI
- [x] M3 — backend contract/integration tests, README two-process runbook, manual smoke note, verification

## Review loops

- Per milestone: implement → critic review → clean-code pass (max 3 loops).

## Critic review notes (pre-execution)

- Round 1: lock API contract, HTTP semantics, **separate Astro frontend** (replaces Thymeleaf), `ConversationTurn` mapping.
- Round 2: expand DoD from spec Phase 6 AC, HTTP unavailable tests, trace exposure, anti-history guards.
- User direction: Thymeleaf rejected; Astro separate app is the locked UI approach.
