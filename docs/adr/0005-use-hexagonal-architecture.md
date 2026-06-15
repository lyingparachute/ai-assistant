# ADR 0005: Use Hexagonal Architecture

## Status

Accepted

## Context

The assistant combines a Chat Interface, application orchestration, local model calls, pgvector RAG, MCP tools, REST Countries, and weather data. The project is a recruitment task, so code quality, testability, and clear boundaries are part of the evaluation.

Without explicit boundaries, controllers could absorb orchestration, infrastructure SDK types could leak into domain code, and tests could become either over-mocked or dependent on unstable external services.

## Decision

Use hexagonal architecture with DDD-inspired boundaries.

The application core owns use cases, domain concepts, and ports. Inbound adapters call application services. Outbound adapters implement ports for Ollama, pgvector, countries MCP, weather MCP, and REST Countries inside the custom MCP server.

DDD will be used pragmatically: explicit language and value objects, focused use-case services, and replaceable adapters without unnecessary aggregates or ceremony.

## Consequences

Benefits:

- Keeps controllers thin and orchestration explicit.
- Makes infrastructure replaceable and testable through ports.
- Supports source-unavailable behavior without scattered fallback logic.
- Helps reviewers see design intent quickly.
- Protects domain concepts from Spring, MCP, database, and HTTP client types where reasonable.

Trade-offs:

- Adds more files and interfaces than a small script-style solution.
- Requires discipline to avoid duplicating simple concepts across modules.
- Can become over-engineered if every small class is treated as an aggregate or service.

## Alternatives Considered

- Controller-first Spring application:
  - Reason rejected: faster initially, but likely to hide orchestration and make source routing harder to test.
- Layered technical packages only:
  - Reason rejected: encourages technical grouping over business capability boundaries.
- Full DDD tactical model:
  - Reason rejected: too ceremonial for this simple domain.

## Verification

- `assistant-app` separates inbound adapters, application services, domain concepts, ports, and outbound adapters.
- `countries-mcp-server` keeps REST Countries HTTP details behind its own port.
- Domain concepts do not import infrastructure SDK types.
- Orchestration tests use controlled ports to prove required request flows.
- Code review checks for controller-heavy logic and adapter leakage.
