# Phase 2 Implementation Notes

## Decisions not explicit in the implementation plan

### MCP Java SDK selection

- Library: `io.modelcontextprotocol.sdk:mcp-core` + `mcp-json-jackson2` at BOM `1.0.0`.
- Jackson 2.x bundle chosen to align with Spring Boot 3.5; Jackson 3 `mcp` aggregator avoided.
- Tool registration uses `McpServer.sync(...).toolCall(...)`, not deprecated `.tool()`.

### Configuration naming

- Implemented `CountriesMcpConfiguration` instead of architecture doc name `CountriesMcpProperties`. Same binding prefix `countries.mcp.*`. Renaming deferred to avoid cross-phase churn.

### Package layout

- Added `model/` for `CountryFacts`, `LookupPlace`, `CountryLookupOutcome` — not listed in architecture tree but keeps application layer free of tool types.
- Skipped `services/RestCountriesLookupService`; name-then-capital orchestration lives in `LookupCountryUseCase` per Phase 2 task list (use case + port, not separate service class).

### Hint ownership

- Application hints in `CountryLookupHints`; MCP error category strings in `CountryToolErrors`. Tool layer maps `CountryLookupOutcome` → envelope.

### MCP vs HTTP timeouts

- MCP `requestTimeout` set to `2 × restCountriesTimeoutSeconds` so name-then-capital fallback can complete within one MCP call.

### Stdio safety

- `logback-spring.xml` logs to stderr only.
- `spring.main.banner-mode: off`, `web-application-type: none`.
- `countries.mcp.stdio-enabled` gates `CountriesMcpServerAdapter`; false in test profile.

### Serialization failures

- `JsonProcessingException` maps to `country lookup failed`, not `REST Countries source unavailable`.

### Test stub server

- JDK `HttpServer` stub matches decoded URI paths (not URL-encoded keys) to mirror `HttpServer` path decoding.

## Tradeoffs

- Manual MCP host smoke not captured; automated coverage exercises tool handler and lifecycle instead.
- Weather entry left in `.mcp.json` from Phase 1 placeholder; countries block only updated in Phase 2.

## Follow-up for other plans

- Phase 3: `CountriesPort`, `CountryInfo`, `CountriesMcpClientAdapter` in `assistant-app`.
- Optional: separate `mcpRequestTimeoutSeconds` property if hosts need finer control than `2 ×` HTTP timeout.
