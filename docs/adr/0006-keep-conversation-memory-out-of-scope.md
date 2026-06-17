# ADR 0006: Keep Conversation Memory Out of Scope

## Status

Accepted

## Context

The assignment explicitly excludes long-term memory and short-term conversational memory beyond a single chat interaction. The project still needs RAG knowledge, tool results, and request-local data, but those must not be described or implemented as memory.

Adding memory would increase storage, privacy, prompt, testing, and user-expectation complexity without helping the required demo questions.

## Decision

Do not implement long-term or short-term conversation memory.

`ConversationTurn` may exist only as request-local data for one assistant request and response. It must not be persisted as assistant memory or used to answer future requests.

RAG knowledge remains CDQ Fraud Guard product-page content. Tool results remain per-request source results.

## Consequences

Benefits:

- Matches assignment scope.
- Keeps implementation focused on required RAG, MCP, and local model behavior.
- Avoids privacy and persistence concerns.
- Prevents confusion between RAG knowledge, tool results, and conversation history.

Trade-offs:

- The assistant will not remember prior user questions across requests.
- Follow-up questions that depend on earlier turns are out of scope unless the Chat Interface sends all needed information in a single request.
- Future memory support would require a separate ADR, data model, tests, and user-facing behavior rules.

## Alternatives Considered

- Persist full chat history:
  - Reason rejected: explicitly out of scope and adds privacy and storage complexity.
- Keep short-term session memory:
  - Reason rejected: still outside the assignment and would complicate demo behavior.
- Treat RAG as memory:
  - Reason rejected: conflicts with project language. RAG knowledge is a knowledge source, not memory.

## Verification

- No persistent conversation-memory tables, repositories, or services are introduced.
- `ConversationTurn`, if implemented, is request-local only.
- README limitations state that long-term and short-term memory are out of scope.
- Tests do not rely on previous chat turns unless a future assignment changes scope.
