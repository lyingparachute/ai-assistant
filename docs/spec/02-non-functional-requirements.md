# Non-Functional Requirements

## Local-First Execution

- The assistant must run on a developer machine without cloud model hosting.
- Ollama, pgvector, the custom countries MCP server, and the weather MCP server must be usable locally.
- External network access may be needed for initial source retrieval or REST Countries calls, but core model execution remains local.

## Reproducibility

- Local setup must be documented in `README.md`.
- Runtime dependencies should be started through repeatable commands or documented profiles.
- Data ingestion for CDQ Fraud Guard content must be repeatable from a clean local database.
- Configuration must not depend on machine-specific paths.

## Observability

- Application logs should show major request steps without exposing sensitive data.
- Failures in model access, vector retrieval, MCP calls, and ingestion should be distinguishable.
- Demo verification should capture enough evidence to show which capabilities were exercised.

## Error Handling

- Missing or unavailable sources must produce explicit user-facing responses.
- The assistant must not silently fall back to model memory for required tool or RAG facts.
- Infrastructure failures should be propagated as typed application outcomes or clear exceptions at the boundary.
- Partial answers are allowed only when the answer identifies unavailable sources.

## Security

- No secrets, API keys, credentials, model names, service URLs, ports, or local filesystem paths may be hardcoded in production logic.
- Runtime configuration must come from documented configuration mechanisms.
- The assistant must not expose local environment details in normal chat responses.
- Downloaded or extracted source content must be treated as untrusted input.

## Maintainability

- Use clear module boundaries aligned with hexagonal architecture.
- Keep business language consistent across code, tests, and documentation.
- Prefer small, focused services and explicit ports.
- Record material architecture decisions as ADRs.
- Keep documentation updated when implementation decisions change.

## Testability

- Important behavior must be covered by automated tests.
- Tests should verify source routing, RAG behavior, MCP integration behavior, and failure modes.
- Integration boundaries should use real local dependencies or controlled stubs when real services would be unstable.
- Demo questions must be reproducible through tests, scripted checks, or documented manual verification.

## Explicit Non-Goals

- Long-term memory.
- Short-term memory beyond a single request flow.
- User accounts or authentication.
- Remote production deployment.
- Cloud LLM providers.
- Paid APIs.
- General web browsing beyond the assignment sources.
