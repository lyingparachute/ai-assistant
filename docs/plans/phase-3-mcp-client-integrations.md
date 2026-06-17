Status: landed — 613e8c5

# Phase 3: MCP Client Integrations

## Scope

- `assistant-app` countries boundary: `CountriesPort`, `CountryInfo`, `CountriesMcpClientAdapter`.
- `assistant-app` weather boundary: `WeatherPort`, `WeatherReport`, `WeatherTimestamp`, `WeatherMcpClientAdapter`.
- Shared `ToolExecutionResult` for typed port outcomes.
- MCP tool invocation behind `McpToolInvoker` (real stdio client in production; stub in tests).
- Typed `AssistantMcpProperties` for countries and weather command, args, env, cwd, transport, timeout, tool names.
- Contract docs: `docs/spec/12-countries-mcp-client-contract.md`, `docs/spec/13-weather-mcp-tool-contract.md`.
- Contract and integration tests with controlled MCP fixtures; no uncontrolled network.
- README weather MCP setup; extend countries MCP client notes.

## Out of scope

- Phase 4+ (RAG, orchestration, chat UI, `LlmPort`).
- Changes to `countries-mcp-server` internals.
- Live MCP subprocesses or live weather API in automated tests.
- Demo answer capture (Phase 7).
- Committing this plan file.

## Definition of Done

- [x] `./mvnw -pl assistant-app test` passes; output recorded in `implementation-notes.md`.
- [x] Germany and Berlin lookups succeed through `CountriesPort` using controlled MCP fixtures.
- [x] Controlled Munich weather MCP response maps to `WeatherReport` with location, temperature, and `WeatherTimestamp` provenance (`Retrieved` when source provides no observed time).
- [x] Countries and weather MCP transport/configuration failures return `SourceUnavailable`; adapters never invent country facts or temperatures.
- [x] Tool-level `ok: false` countries envelope maps to `ToolError`, not invented facts.
- [x] Malformed weather payload surfaces clear adapter boundary failure (`SourceUnavailable` or `ToolError` with parse reason).
- [x] Contract tests pin countries and weather MCP schema mappings and error shapes.
- [x] README documents weather MCP installation, env vars, local startup, optional manual verification tag.

## Milestones

- [x] M1 — Client contract documentation (`12`, `13`) and plan
- [x] M2 — Domain tools package (`ToolExecutionResult`, ports, value objects)
- [x] M3 — MCP invoker + countries adapter + contract/integration tests
- [x] M4 — Weather adapter + configuration + tests + README + implementation notes + AI usage log

## Review loops

- Per milestone: implement → critic review → clean-code pass (max 3 loops).
