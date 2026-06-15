---
name: hexagonal-architecture
description: Hexagonal boundaries for the Local AI Assistant. Use when placing code across modules, defining ports, or wiring adapters.
---

# Hexagonal Architecture

Authoritative detail: `docs/spec/05-architecture.md`, `docs/spec/10-code-quality-guidelines.md` section 3, and ADR `0005`. This skill is the checklist.

## When to Use

Use when deciding where a class belongs, defining a new port, adding an adapter, or wiring modules.

## Rules

- Keep the four roles separate: inbound adapters, application services, domain ports, outbound adapters.
- Dependencies point inward. Domain depends on nothing outside itself. Adapters depend on the application core, never the reverse.
- The application defines ports (`LlmPort`, `RagKnowledgePort`, `CountriesPort`, `WeatherPort`); adapters implement them.
- The assistant application uses ports only. It never calls Ollama, pgvector, REST Countries, or MCP SDK types directly.
- Domain and application code must not import Spring, JDBC, HTTP clients, the MCP SDK, or Spring AI types. Spring AI types are allowed only in outbound adapters and configuration.
- Package by business capability first (`question`, `orchestration`, `rag`, `tools`, `llm`), then by adapter.
- `countries-mcp-server` keeps REST Countries behind its own port. `assistant-app` does not depend on its internals. `shared-kernel` is conditional (created only for a concrete cross-module type) and stays minimal.

## Patterns to Prefer

- Port interface in the application core; adapter implementation under `adapters/outbound/...`.
- Application service that orchestrates ports and a `ResponseComposer`; deterministic source routing in code.
- DDD used pragmatically: explicit value objects and focused use-case services, no ceremonial aggregates.
- Controlled test adapters substituted for infrastructure in tests.

## Patterns to Avoid

- Orchestration inside controllers or inside outbound adapters.
- Infrastructure SDK types appearing in domain or application packages.
- `assistant-app` importing `countries-mcp-server` internals, or cross-module reach-through.
- Promoting one-module concepts into `shared-kernel`.
- Technical top-level packages (`controllers`, `services`, `repositories`).

## Verification Checklist

- A dependency-direction scan shows no infrastructure import in domain or application code.
- Each external capability is reached only through its port.
- Orchestration tests use controlled ports to prove the required demo request flows.
- Module boundaries hold: no `assistant-app` dependency on `countries-mcp-server` internals.
