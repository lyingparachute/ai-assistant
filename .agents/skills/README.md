# Repository AI Skills

Local AI skills are repository-specific guidance for coding agents working on the Local Java AI Assistant. They capture conventions too specific for global agent rules: architecture boundaries, testing style, and integration patterns for this recruitment task.

Skills are the focused, day-to-day checklists. `AGENTS.md` is the contract, and `docs/spec/10-code-quality-guidelines.md` holds the full detail. Skills reference those documents instead of duplicating them.

Read the relevant skill before working on the matching area, and read the documents it links before implementing.

## Skill Index

| Skill | Use it when you are working on |
| --- | --- |
| `clean-java` | Any Java code: value objects, immutability, null handling, exceptions, focused classes, package-private helpers. |
| `spring-boot` | Spring wiring: constructor injection, `@ConfigurationProperties`, profiles, controllers as inbound adapters. |
| `hexagonal-architecture` | Module and package boundaries, ports and adapters, dependency direction, keeping the domain independent. |
| `mcp` | The countries MCP server or weather MCP integration: semantic tools, JSON schemas, structured errors, lifecycle, `.mcp.json`. |
| `rag-pgvector` | RAG ingestion and retrieval: extraction, chunking, embeddings, metadata, pgvector storage, retrieval thresholds. |
| `testing` | Writing tests: unit, contract, integration, Testcontainers, controlled adapters, E2E demo verification. |
| `documentation` | Keeping `CONTEXT.md`, `docs/spec/`, `docs/adr/`, `README.md`, and `docs/ai/` current and honest. |

DDD-inspired language is not a separate skill. It runs through `clean-java`, `hexagonal-architecture`, and the language rules in `CONTEXT.md` and `AGENTS.md`.

## Skill Expectations

Each skill is concise, actionable, and specific to this repository. Each one states:

- when to use it;
- rules to follow;
- patterns to prefer;
- patterns to avoid;
- a verification checklist.

Skills guide implementation. They do not introduce production logic by themselves, and they do not replace documentation, ADRs, tests, or human review. Every skill ends with a verification step because in this repository, work is done only when its output has been verified, not asserted.
