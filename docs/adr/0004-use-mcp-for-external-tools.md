# ADR 0004: Use MCP for External Tools

## Status

Accepted

## Context

The assistant must answer country-related questions through a custom MCP server backed by REST Countries. It must answer current-weather questions through the local `semdin/mcp-weather` MCP server.

The application needs tool integrations that are replaceable and testable. The assistant must surface tool failures instead of fabricating missing facts.

## Decision

Use MCP for country and weather tools.

Build a custom countries MCP server that calls REST Countries. Integrate the local weather MCP server from `semdin/mcp-weather` through an MCP client adapter.

The assistant application will depend on `CountriesPort` and `WeatherPort`, not MCP SDK types directly.

MCP tools must be semantic assistant-facing capabilities, not one-to-one mirrors of upstream REST APIs. Tool schemas should use clear names, compact descriptions, required inputs, minimal outputs, and recoverable error messages.

Local MCP servers should be started from declarative configuration where practical, with command, args, env, working directory, transport, and timeout documented. Server lifecycle must include clean initialization and graceful shutdown.

## Consequences

Benefits:

- Satisfies the assignment's MCP requirements.
- Keeps assistant tool behavior behind explicit ports.
- Allows countries and weather integrations to be tested with controlled adapters or test servers.
- Separates REST Countries details from assistant orchestration.
- Reduces tool noise by exposing only capabilities the assistant needs for demo behavior.

Trade-offs:

- Adds local process startup and configuration complexity.
- MCP transport settings must be documented and tested.
- Tool schema changes can break adapters if contract tests are missing.
- Overly generic tool schemas can make the model choose tools poorly.
- Poor lifecycle handling can leave local MCP processes hanging after demo or test runs.
- Live external country and weather sources may be unavailable during demo.

## Alternatives Considered

- Direct REST Countries and weather HTTP calls from `assistant-app`:
  - Reason rejected: bypasses the assignment's MCP requirement and couples assistant orchestration to external APIs.
- Implement weather integration as a custom HTTP adapter:
  - Reason rejected: the assignment points to a local MCP weather server, and direct HTTP would not prove MCP integration.
- Put REST Countries logic inside the assistant application:
  - Reason rejected: weakens module boundaries and makes the custom countries MCP deliverable less meaningful.

## Verification

- Germany capital lookup goes through the countries MCP path.
- Munich weather lookup goes through the weather MCP path.
- Tests cover successful tool results, schema shape, recovery hints, and source-unavailable outcomes.
- Startup, timeout, and shutdown behavior are covered by focused verification.
- Demo evidence records which source path each required question used.
