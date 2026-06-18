# Weather MCP Tool Contract

Captured from `semdin/mcp-weather` (`src/index.ts` on GitHub `main` as of Phase 3). The assistant consumes this server through `WeatherMcpClientAdapter` behind `ResolveWeatherObservation`.

## MCP Transport

- Transport: stdio JSON-RPC (subprocess launched from `.mcp.json` or `assistant.mcp.weather` configuration).
- Server name announced: `semdin-weather-mcp` version `1.0.0`.
- Required environment variables:
  - `WEATHER_API_KEY` — API key for the configured weather provider.
  - `WEATHER_API_URL` — Provider base URL (for example `https://api.weatherapi.com/v1/current.json`).

## Semantic Tool: `get-weather`

### Input schema

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `city` | string | yes | City name (for example `Munich`). |

### Success output

The server returns a single MCP text content block (not JSON):

```text
the weather in Munich is currently: 12.3
```

Pattern (case-insensitive city segment in source; adapter normalizes location from requested city):

```text
the weather in <city> is currently: <temperature_celsius>
```

The upstream JSON includes `current.last_updated`, but the server does not expose it. The assistant adapter records a `WeatherTimestamp.Retrieved` at mapping time. A retrieval time is never relabeled as observed.

### Error output

HTTP or provider failure returns:

```text
Some error occured.
```

Transport or tool exceptions may surface as MCP tool errors.

## Port mapping

| Port | Method | Input | Output |
| --- | --- | --- | --- |
| `ResolveWeatherObservation` | `execute(Command)` | Non-blank `Location` | `ToolExecutionResult<WeatherReport>` |

### Value object: `WeatherReport`

| Field | Type | Notes |
| --- | --- | --- |
| `location` | `Location` | Requested city name preserved for display |
| `temperature` | `Temperature` | Celsius from parsed text |
| `timestamp` | `WeatherTimestamp` | `Retrieved` for this server contract |

Blank location is rejected by `Location` before the MCP adapter is called.

### Outcome mapping

| MCP signal | Port outcome |
| --- | --- |
| Text matches success pattern | `Success(WeatherReport)` |
| Text `Some error occured.` | `SourceUnavailable` labeled `weather MCP` |
| Text matches no pattern | `SourceUnavailable` (`malformed weather tool payload`) |
| Subprocess / timeout / uninitialized client | `SourceUnavailable` labeled `weather MCP` |

Adapters must not invent temperatures when the source is unavailable.

## Configuration (`assistant.mcp.weather`)

| Property | Environment variable | Default | Description |
| --- | --- | --- | --- |
| `command` | `ASSISTANT_MCP_WEATHER_COMMAND` | `mcp-weather` | MCP server launch command |
| `args` | — | `[]` | Command arguments |
| `env` | `WEATHER_API_KEY` | — | Provider credentials (not committed) |
| `transport` | — | `stdio` | Only stdio supported in Phase 3 |
| `timeout-seconds` | `ASSISTANT_MCP_WEATHER_TIMEOUT_SECONDS` | `60` | MCP request timeout |
| `tool-name` | — | `get-weather` | Semantic tool name |

Subprocess cwd is not configurable; see the shared note in
[docs/spec/12](12-countries-mcp-client-contract.md#mcp-subprocess-cwd).

`WEATHER_API_URL` must be supplied in subprocess `env` for a live run; document in README, never commit secrets.

## Local startup

1. Install `semdin/mcp-weather` per upstream README (`npm` / `npx tsx` or packaged `mcp-weather` binary).
2. Set `WEATHER_API_KEY` and `WEATHER_API_URL` in the host environment or `.mcp.json` `env` block.
3. Optional manual verification: tag `@Tag("manual")` integration test or README smoke steps against a live server.

## Test fixtures

Controlled fixtures under `assistant-app/src/test/resources/fixtures/mcp/weather/`. Automated tests stub `shared.mcp.McpToolInvoker` responses; they do not call live weather APIs.
