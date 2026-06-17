# Phase 3: MCP Client Integrations

## Task

Implement assistant-side MCP client adapters for countries and weather per `docs/spec/06-implementation-plan.md` Phase 3.

## Agent role

Implementation agent (Cursor).

## Files changed

- `docs/spec/12-countries-mcp-client-contract.md`
- `docs/spec/13-weather-mcp-tool-contract.md`
- `assistant-app/` ports, adapters, configuration, tests, fixtures
- `README.md`, `.mcp.json`, `implementation-notes.md`

## Human review

Pending reviewer confirmation after merge.

## Verification evidence

```text
./mvnw -pl assistant-app test
Tests run: 18, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Automated tests use `StubMcpToolInvoker`; no live MCP subprocesses or external APIs in CI.
