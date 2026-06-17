Status: landed — 6db98bb

# Phase 2: Countries MCP Server

## Scope

- Custom `countries-mcp-server` module with stdio MCP transport and `country_lookup` tool.
- REST Countries v3.1 outbound adapter behind `RestCountriesPort`.
- Tool contract documentation in `docs/spec/11-countries-mcp-tool-contract.md`.
- Contract and integration tests with stubbed REST Countries HTTP server.
- `.mcp.json` and README local run instructions.

## Out of scope

- `assistant-app` `CountriesPort` / MCP client adapter (Phase 3).
- Weather MCP integration (Phase 3).
- `shared-kernel` module.
- Live REST Countries calls in automated tests.
- Demo answer capture.

## Definition of Done

- [x] `./mvnw -pl countries-mcp-server test` passes (18 tests, BUILD SUCCESS).
- [x] Germany lookup through MCP tool returns Berlin with `countryName`, `capital`, `region`, `population` from controlled fixtures.
- [x] Berlin lookup through MCP tool resolves Germany with same fields.
- [x] Unrecognized input returns structured tool error with recovery hint; server stays alive (tool-level tests).
- [x] REST Countries HTTP 503 and read timeout return source-unavailable structured tool error.
- [x] Ambiguous capital returns structured tool error with country names in hint.
- [x] Contract tests pin tool name, schema, output fields, error envelope; no raw upstream JSON.
- [x] Startup rejects blank base URL and non-positive timeout before MCP registration.
- [x] Lifecycle test covers start/stop idempotency and `server.close()`.
- [x] `.mcp.json` documents stdio transport, timeout, cwd, env; README documents manual smoke run.
- [x] Logs routed to stderr; Spring banner off; `web-application-type: none`.
- [x] Manual stdio smoke from MCP host recorded (2026-06-15; `country_lookup`/`Germany` returned source-unavailable — live REST Countries v3.1 deprecated; MCP stdio path verified via `tools/list`).

## Milestones

- [x] M1 — Tool contract documentation
- [x] M2 — Server implementation (hexagonal slice + MCP SDK)
- [x] M3 — Tests and fixtures
- [x] M4 — Runbook, `.mcp.json`, AI usage log, implementation notes

## Review loops

- Loop 1 critic: 18 findings → Important items addressed.
- Loop 2 clean code: 5 findings → boundary hints split, MCP timeout doubled, `CountryToolErrors` centralized.
