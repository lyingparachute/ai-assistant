---
name: mcp
description: MCP tool design and lifecycle for the Local AI Assistant. Use for the countries MCP server and the weather MCP integration.
---

# MCP Tools

Authoritative detail: `docs/spec/05-architecture.md` section 9, `docs/spec/10-code-quality-guidelines.md` section 8, and ADR `0004`. This skill is the checklist.

## When to Use

Use when building the custom countries MCP server, integrating the `semdin/mcp-weather` server, or designing any MCP tool schema, error, or lifecycle behavior.

## Rules

- Tools are semantic, assistant-facing capabilities, not one-to-one mirrors of REST Countries or the weather API.
- Tool names, descriptions, and JSON schemas must be understandable without external documentation.
- Tool outputs include only what the assistant needs, plus recovery hints for invalid input or unavailable sources.
- Use a consistent result envelope carrying either result data or a structured error with a recovery hint. Expected failures return a structured tool error; they do not crash the server.
- Recovery hints state what to do next: available options, corrected ranges, or a "did you mean" suggestion for a likely typo.
- MCP SDK types stay inside adapters. The assistant sees only `CountriesPort` and `WeatherPort`.
- Source routing for required demo questions is deterministic in application code, not delegated to an autonomous model loop. Any future loop needs max turns, timeout, cancellation, and typed `{ ok, error, hint }` results.
- Structure the countries server as: core server factory, one tool class per semantic tool, schema definitions, REST Countries service classes, typed error helpers, and configuration from environment or local profile.
- Lifecycle: validate startup configuration before tool registration, initialize before emitting MCP notifications or logs, handle SIGINT/SIGTERM cleanly, apply call timeouts.
- Local startup is declarative (a `.mcp.json` entry with command, args, env, working directory). No machine-specific paths committed.

## Patterns to Prefer

- A `country_lookup` style semantic tool that accepts a country name or a capital-city name and returns compact `CountryInfo` fields, not raw REST Countries JSON.
- Typed error helpers returning a structured tool error with a clear, actionable hint.
- A small registry at the infrastructure edge mapping configured MCP tools to typed ports.
- Contract tests that pin tool names, required fields, and error shapes.

## Patterns to Avoid

- Exposing upstream-only fields or raw provider payloads through tool output.
- Generic errors such as "something went wrong" with no recovery hint.
- Crashing the server on an expected invalid input.
- Leaking MCP SDK types into the assistant application.
- An unbounded model-driven tool loop for the required demo paths.
- Hardcoded transport settings, ports, or local paths in tool logic.

## Verification Checklist

- Germany lookup returns Berlin through the MCP tool path; invalid country returns a typed result with a recovery hint.
- Upstream failure is surfaced as a source failure without crashing the server.
- A contract test pins tool schema names, required fields, and error shapes; no upstream-only fields leak.
- Startup validates configuration; shutdown leaves no hanging local process; call timeouts apply.
- No fabricated tool results: an unavailable source yields a source-unavailable outcome, never an invented value.
