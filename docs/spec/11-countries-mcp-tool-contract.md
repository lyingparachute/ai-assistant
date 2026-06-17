# Countries MCP Tool Contract

## MCP Transport and Library

- Transport: stdio JSON-RPC (process subprocess launched from `.mcp.json`).
- Java library: `io.modelcontextprotocol.sdk:mcp-core` with `mcp-json-jackson2` (Jackson 2.x, aligned with Spring Boot 3.5).
- Server lifecycle: validate configuration before tool registration, log to stderr only, handle SIGINT/SIGTERM through a shutdown hook.

## Semantic Tool: `country_lookup`

Assistant-facing capability for country facts. It is not a mirror of REST Countries endpoints.

### Input schema

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `name` | string | yes | English country name (for example `Germany`) or capital city name (for example `Berlin`). |

### Success output envelope

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

| Field | Type | Description |
| --- | --- | --- |
| `countryName` | string | Common English country name from REST Countries. |
| `capital` | string | Primary capital city. |
| `region` | string | Geographic region (for example `Europe`). |
| `population` | number | Latest population figure from REST Countries. |

### Error output envelope

```json
{
  "ok": false,
  "error": "country name is not recognized",
  "hint": "Provide an English country name or capital city such as Germany or Berlin."
}
```

| Field | Type | Description |
| --- | --- | --- |
| `error` | string | Short failure category. |
| `hint` | string | Recovery guidance for the assistant or reviewer. |

Expected failures return `ok: false` with a recovery hint. They do not crash the MCP server process.

### Error categories

| Category | When | Example hint |
| --- | --- | --- |
| `country name is not recognized` | No country matches the name or capital input. | Suggest valid English country or capital names. |
| `capital city matches more than one country` | REST Countries returns multiple countries for one capital. | Name the ambiguous countries and ask for a country name instead. |
| `REST Countries source unavailable` | HTTP error, timeout, or malformed upstream response. | Retry later; do not invent country facts. |
| `name is required` | Empty or blank `name` input. | Provide a non-empty country or capital name. |

## REST Countries Integration

### API version and base URL

- Version: v3.1
- Base URL (configurable, default): `https://restcountries.com/v3.1`
- Authentication: none (public API)

### Endpoints used

| Purpose | Endpoint | Notes |
| --- | --- | --- |
| Country name lookup | `GET /name/{name}?fields=name,capital,region,population` | Case-insensitive fuzzy match. |
| Capital city lookup | `GET /capital/{capital}?fields=name,capital,region,population` | Used when name lookup returns no result. |

### Field mapping

| REST Countries v3.1 field | Tool output field |
| --- | --- |
| `name.common` | `countryName` |
| `capital[0]` | `capital` |
| `region` | `region` |
| `population` | `population` |

### Lookup behavior

1. Validate `name` is non-blank.
2. Query `/name/{name}` with the configured field filter.
3. If the name response is empty or HTTP 404, query `/capital/{name}`.
4. If the capital response contains more than one country, return `capital city matches more than one country` with a hint listing the common country names.
5. If the name response contains more than one country, prefer the entry whose `name.common` equals the input ignoring case; if still ambiguous, return a not-recognized error with a hint.
6. Map the selected country JSON object into the success envelope. Raw upstream JSON is never returned through the tool.

### Upstream failure handling

| Upstream signal | Tool outcome |
| --- | --- |
| HTTP 404 on both name and capital paths | `country name is not recognized` |
| HTTP 5xx, connection failure, read timeout | `REST Countries source unavailable` |
| Empty JSON array | `country name is not recognized` |
| Missing required mapped fields | `REST Countries source unavailable` |

## Configuration

| Property | Environment variable | Default | Description |
| --- | --- | --- | --- |
| `countries.mcp.rest-countries-base-url` | `COUNTRIES_MCP_REST_COUNTRIES_BASE_URL` | `https://restcountries.com/v3.1` | REST Countries API base URL. |
| `countries.mcp.rest-countries-timeout-seconds` | `COUNTRIES_MCP_REST_COUNTRIES_TIMEOUT_SECONDS` | `10` | HTTP read timeout for REST Countries calls. |
| `countries.mcp.request-timeout-seconds` | `COUNTRIES_MCP_REQUEST_TIMEOUT_SECONDS` | `20` | MCP server-side request timeout for a single tool call. Distinct from the assistant client-side `ASSISTANT_MCP_COUNTRIES_TIMEOUT_SECONDS` (spec 12). |
| `countries.mcp.server-name` | `COUNTRIES_MCP_SERVER_NAME` | `countries-mcp-server` | MCP server name announced to hosts. |
| `countries.mcp.server-version` | `COUNTRIES_MCP_SERVER_VERSION` | `0.1.0-SNAPSHOT` | MCP server version announced to hosts. |

Invalid configuration (blank base URL, non-positive timeout) fails startup before tool registration.
