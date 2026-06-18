# ADR 0009: Stream the Assistant API over SSE with an application output-sink boundary

## Status

Proposed — 2026-06-17. Move to **Accepted** in the same change that starts implementation
M1 of `docs/plans/stream-chat-answers-and-source-usage-trace.md`.

## Context

`POST /api/chat` is a blocking, single-JSON endpoint (`ChatController`): the use case runs to
completion and the whole `ChatResponse` (`answerText`, `sources`, `traceCorrelationId`) is
serialized at once. The Chat Interface renders the answer in one `textContent` assignment after
a single `response.json()`, and shows source provenance only as a static list after the fact.

Two improvements were designed (see
`docs/plans/stream-chat-answers-and-source-usage-trace.md`): deliver the answer incrementally
(Streamed Answer) and show each Knowledge Source's outcome as it is determined (live
Source-Usage Trace). Both are presentation changes; neither may alter a fact or weaken the
honesty guarantees in `CLAUDE.md` §8.

Verified facts (source verification 2026-06-17; re-run after hygiene dependency bumps):

- The LLM is invoked on only 2 of 6 routes (`PlaceSynthesis`, `CdqProduct`); the other four
  compose deterministically with no model call. Only those two have tokens to stream.
- RAG grounding is established before any synthesis (insufficient/unavailable retrieval
  short-circuits without calling the LLM).
- `LlmPort.generate` is blocking and returns a validated `LlmResult`; the blank/failed →
  `SourceUnavailable` guarantee lives in `OllamaLlmAdapter`.
- `ChatModel` extends `StreamingChatModel` in Spring AI 1.1.x; `OllamaLlmAdapter`'s existing
  `ChatModel` field already supports `.stream(Prompt)`. `reactor-core` is transitive via Spring AI.
- In-repo `/api/chat` consumers: Chat Interface, e2e client, `scripts/capture-demo-answers.sh`,
  demo curl docs, and controller integration tests. No external clients.

The constraint set: domain and application code must not import Spring AI, reactor `Flux`, or
SSE/HTTP types (ADR `0001`, `0005`); deterministic routes and honesty behaviour preserved.

## Decision

Make `POST /api/chat` a Server-Sent Events endpoint (`text/event-stream`) whose **terminal
event is authoritative**, and bridge streaming to the application core through an
**application-owned output port**, keeping reactor and SSE types at the edges.

- **Streaming-only endpoint.** No parallel JSON endpoint. Terminal `final` event carries a
  **semantically equal** `ChatResponse` payload; all in-repo consumers migrate together.
- **Four-event protocol.** `trace { type, status }` (`type` matches today's `SourceResponse.type`;
  `status` is `USED|UNAVAILABLE|INSUFFICIENT`); `token { text }` (synthesis routes only);
  `final { ChatResponse }` (exactly one — includes honest source-unavailable answers);
  `error { ApiErrorResponse }` (unexpected failure **after** the stream has opened only).
  Request validation → HTTP 400 JSON **before** `SseEmitter` is returned; handler sets
  `Content-Type: application/json` on error bodies.
- **`AssistantResponseSink` (application port).** Per-request instance (not a Spring bean).
  Methods: `recordSourceOutcome(SourceType, SourceContributionStatus)`, `appendAnswerToken(String)`,
  `complete(ConversationTurn)`, `failUnexpected(errorCode, message)`. Inbound HTTP adapter
  implements the port and maps to SSE. No HTTP types on the port.
- **Use case API.** `ConversationTurn answer(UserQuestion question, AssistantResponseSink sink)` —
  replaces parameterless `answer`. Controller constructs sink, runs use case on bounded executor.
- **`AssistantChatProperties`.** Typed `assistant.chat.*` config (stream timeout, executor pool/queue)
  per refactor-3b record pattern; binds inbound SSE executor and `SseEmitter` timeout.
- **`TokenSink` on `LlmPort`.** `generate(PromptContext, TokenSink)` returns authoritative
  `LlmResult`. `OllamaLlmAdapter` streams via cancellable `Flux` subscription (not
  `flux.toStream()`), with `Flux.timeout`, WebClient timeouts on the **chat** `OllamaApi` bean,
  blank-delta skipping, and verified delta-not-cumulative semantics.
- **Failure model.** Post-stream use-case/adapter failures → `error` event (HTTP 200 stream),
  not HTTP 500. Pre-stream validation → HTTP 400 JSON.
- **Honesty model.** Streaming is additive; `ResponseComposer` + complete `LlmResult` unchanged;
  `final` replaces provisional tokens. Chat UI uses text DOM APIs only for streamed tokens.

## Consequences

Benefits:

- Live Source-Usage Trace and Streamed Answer make honesty visible without changing facts.
- Terminal payload preserves e2e demo assertions after client migration.
- Reactor and SSE confined to adapters.

Trade-offs:

- Transport breaking change for all in-repo consumers including demo capture script.
- Bounded async path (`SseEmitter` + executor) and reactive-to-blocking bridge to verify.
- `LlmPort` and use-case public API signature change.

## Alternatives Considered

- **Parallel `/api/chat/stream`:** rejected — orphan JSON becomes dead code.
- **Content negotiation:** rejected — dual shapes complicate contract/tests.
- **Reactive use case (`Flux`):** rejected — leaks reactor inward (ADR `0001`/`0005`).
- **Fake typewriter over complete answer:** rejected — not real token streaming.

## Verification

- Live + tests per `docs/plans/stream-chat-answers-and-source-usage-trace.md` Definition of Done.
- Grep: no Spring AI / reactor / SSE in domain application packages.
- `./mvnw -pl e2e-tests verify -P e2e` green; `capture-demo-answers.sh` extracts `final` JSON.
