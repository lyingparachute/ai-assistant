# Architecture Decision Records

Architecture Decision Records describe material technical decisions and the reasons behind them. They are useful in this project because reviewers should be able to see why a framework, boundary, integration pattern, or trade-off was chosen.

Create an ADR when a decision affects architecture, dependencies, runtime topology, data model, testing strategy, or public behavior.

## Index

| ADR | Title | Status |
| --- | --- | --- |
| [0001](0001-use-spring-ai.md) | Use Spring AI | Accepted |
| [0002](0002-use-ollama-qwen3-4b.md) | Use Ollama qwen3:4b | Accepted |
| [0003](0003-use-pgvector-for-rag.md) | Use pgvector for RAG | Accepted |
| [0004](0004-use-mcp-for-external-tools.md) | Use MCP for external tools | Accepted |
| [0005](0005-use-hexagonal-architecture.md) | Use hexagonal architecture | Accepted |
| [0006](0006-keep-conversation-memory-out-of-scope.md) | Keep conversation memory out of scope | Accepted |
| [0007](0007-use-ollama-embedding-model.md) | Use Ollama embedding model | Accepted |
| [0008](0008-migrate-rest-countries-to-v5.md) | Migrate REST Countries to v5 | Accepted |
| [0009](0009-stream-assistant-api-over-sse.md) | Stream the assistant API over SSE | Accepted |
| [0010](0010-bounded-agentic-tool-orchestration.md) | Bounded agentic tool orchestration (opt-in) | Proposed |

## Suggested Naming

Use a sequential filename:

```text
0001-short-decision-title.md
```

## ADR Template

```markdown
# ADR 0000: Decision Title

## Status

Proposed | Accepted | Superseded

## Context

What problem or constraint requires a decision?

## Decision

What decision was made?

## Consequences

What improves, what becomes harder, and what trade-offs are accepted?

## Alternatives Considered

- Alternative:
  - Reason rejected:

## Verification

How will this decision be verified in code, tests, documentation, or local runtime behavior?
```
