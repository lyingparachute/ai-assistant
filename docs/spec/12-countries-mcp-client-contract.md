# Countries MCP Client Contract

Assistant-side mapping for the `country_lookup` tool defined in `docs/spec/11-countries-mcp-tool-contract.md`. The assistant application never imports `countries-mcp-server` types; MCP JSON is the process boundary.

## Port

| Port | Method | Input | Output |
| --- | --- | --- | --- |
| `ResolveCountryFacts` | `execute(Command)` | Non-blank English country or capital name | `ToolExecutionResult<CountryInfo>` |

Blank input is rejected by `ResolveCountryFacts.Command` before the MCP adapter is called.

## Value object: `CountryInfo`

| Field | Type | Source field |
| --- | --- | --- |
| `countryName` | string | `data.countryName` |
| `capital` | string | `data.capital` |
| `region` | string | `data.region` |
| `population` | long | `data.population` |

Constructed only from a successful tool envelope. Population must be non-negative.

## MCP tool mapping

| MCP item | Client constant | Notes |
| --- | --- | --- |
| Tool name | `country_lookup` | Must match `CountryToolSchemas.TOOL_NAME` on server |
| Input | `{ "name": "<place>" }` | Single required string field |
| Success | `ok: true` + `data` object | Map to `CountryInfo` |
| Tool error | `ok: false` + `error` + `hint` | Map to `ToolError`; never invent facts |
| Transport / parse failure | — | Map to `SourceUnavailable` labeled `countries MCP` |

### Success envelope (fixture reference)

```json
{
  "ok": true,
  "data": {
    "countryName": "Germany",
    "capital": "Berlin",
    "region": "Europe",
    "population": 83240525
  }
}
```

### Tool error envelope (fixture reference)

```json
{
  "ok": false,
  "error": "country name is not recognized",
  "hint": "Provide an English country name or capital city such as Germany or Berlin."
}
```

### Source-unavailable envelope (fixture reference)

```json
{
  "ok": false,
  "error": "REST Countries source unavailable",
  "hint": "The REST Countries service could not be reached. Retry later; do not invent country facts."
}
```

## Adapter behavior

1. Validate and normalize non-blank `Command.name` before MCP call.
2. Invoke `country_lookup` through `McpToolInvoker`.
3. Parse the first text content block as JSON.
4. When `ok` is true, require all `data` fields; missing fields → `SourceUnavailable` (`malformed countries tool payload`).
5. When `ok` is false, return `ToolError` with server `error` and `hint`.
6. MCP `isError` flag with unparseable content → `SourceUnavailable`.
7. Subprocess start failure, timeout, or uninitialized client → `SourceUnavailable` labeled `countries MCP`.

Raw REST Countries JSON must never appear in `CountryInfo`.

## Configuration (`assistant.mcp.countries`)

| Property | Environment variable | Default | Description |
| --- | --- | --- | --- |
| `command` | `ASSISTANT_MCP_COUNTRIES_COMMAND` | `./mvnw` | MCP server launch command |
| `args` | — | `-q,-pl,countries-mcp-server,spring-boot:run` | Command arguments |
| `env` | — | REST Countries URL + timeout from Phase 2 | Subprocess environment |
| `transport` | — | `stdio` | Only stdio supported in Phase 3 |
| `timeout-seconds` | `ASSISTANT_MCP_COUNTRIES_TIMEOUT_SECONDS` | `60` | MCP request timeout |
| `tool-name` | — | `country_lookup` | Semantic tool name |

<a id="mcp-subprocess-cwd"></a>
**Subprocess working directory (shared note for countries and weather MCP servers).** The MCP server
subprocess cwd is not configurable: `io.modelcontextprotocol.sdk` 1.0.0 `ServerParameters.Builder`
exposes no cwd setter. The subprocess therefore inherits the assistant JVM's cwd. `spring-boot:run`
launches that JVM from `assistant-app/`, which is why the countries jar and weather launcher are
referenced through paths relative to `assistant-app/` (for example
`../countries-mcp-server/target/countries-mcp-server-<version>.jar`). Changing cwd needs an SDK
upgrade or a wrapper command that sets it; the current behavior is documented here as a known SDK
constraint.

Secrets must not be committed. Assignment defaults may live in `application.yml` when safe.

## Test fixtures

Controlled fixtures live under `assistant-app/src/test/resources/fixtures/mcp/countries/`. Tests use `shared.mcp.McpToolInvoker` stubs; they do not spawn the countries server or call REST Countries.
