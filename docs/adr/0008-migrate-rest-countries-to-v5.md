# ADR 0008: Migrate countries-mcp-server from REST Countries v3.1 to v5

## Status

Accepted — 2026-06-17. Population is retained (live contract verified).

## Context

`countries-mcp-server` resolves country facts through REST Countries
(`docs/spec/05-architecture.md`, ADR `0004`). The outbound adapter
(`countries-mcp-server/.../restcountries/RestCountriesHttpAdapter.java`) targets
`https://restcountries.com/v3.1` and parses the v3.1 JSON-array response into
`CountryFacts` (name, capital, region, population — all required).

As of the 2026-06-16 demo capture, every REST Countries v3.1 request returns a
deprecation error. Re-verified 2026-06-17:

```bash
curl -sSL 'https://restcountries.com/v3.1/name/Germany?fields=name,capital'
# {"success": false, "errors": [{"message": "This API version has been deprecated. ... v5 ..."}]}
```

v3.1 is permanently dead. This blocks four of the six Phase 7 demo questions
(Germany capital, Munich-via-capital weather, combined, Berlin place-synthesis);
each routes through a live country fact. The CDQ product question is unaffected (RAG).

The successor is v5. The contract below was verified against a **live authenticated
v5 response** on 2026-06-17 (Germany and France), which is authoritative over the
public field-reference docs (the docs claim `population` is "no read", but the live
API returns it):

- Base URL: `https://api.restcountries.com/countries/v5`.
- Name lookup: `GET /names.common/{value}?response_fields=names.common,capitals,region,population`.
- Capital lookup: `GET /capitals/{value}?response_fields=...` (plural; replaces v3.1 `/capital/`).
- Auth: `Authorization: Bearer {API_KEY}` required (v3.1 was unauthenticated). Missing
  key → HTTP 401. Free tier: 500 requests/month.
- Success body (nested JSON:API), confirmed live:
  ```json
  {"data":{"objects":[{
    "names":{"common":"Germany"},
    "capitals":[{"name":"Berlin","attributes":{"primary":true},"coordinates":{...}}],
    "region":"Europe",
    "population":83497147
  }],"meta":{"total":1,"count":1,...}}}
  ```
  Field paths: `data.objects[].names.common`, `data.objects[].capitals[].name`
  (objects, not strings; `attributes.primary` flags the primary capital),
  `data.objects[].region`, `data.objects[].population` (number, **available**).
- Not found: HTTP **200** with `data.objects: []` (not HTTP 404).

All four fields `CountryFacts` requires are retrievable, so v5 supports the existing
domain model unchanged — no field needs to be dropped.

## Decision

Migrate `countries-mcp-server` to REST Countries v5, keeping REST Countries as the
provider and `CountryFacts` (name, capital, region, population) unchanged. This is an
**outbound-adapter and configuration change only**; the port, domain model, MCP tool
name, input schema, and output JSON envelope are all unchanged.

- Base URL default becomes the v5 URL; name lookup uses `/names.common/{value}`,
  capital lookup uses `/capitals/{value}`, both with
  `response_fields=names.common,capitals,region,population`. All via typed
  `countries.mcp` config — no literals in business logic.
- The adapter sends `Authorization: Bearer {API_KEY}` from config bound to
  `REST_COUNTRIES_API_KEY`. The key is a secret: never committed,
  logged, or defaulted to a literal.
- The API key is **optional config** (no `@NotBlank` startup gate). When absent,
  blank, or rejected (401/403), the adapter returns the existing
  `RestCountriesQueryResult.SourceUnavailable` with an auth-specific hint — a named
  source failure, never a fabricated answer and never a boot failure. This resolves
  the fail-fast-vs-graceful tension in favour of graceful source-unavailable.
- Response mapping parses the nested v5 shape into `CountryFacts`: `names.common`,
  `region`, `population`, and the capital from `capitals[]` (prefer
  `attributes.primary`, else first entry).
- Not-found detection: empty `data.objects` → `NotFound` (v5 returns HTTP 200, not
  404). Non-2xx and parse failures → `SourceUnavailable`; the v5 error envelope
  `{"errors":[{"message":...}]}` is handled distinctly from the success body.

## Consequences

Benefits:

- Restores live country facts, unblocking four Phase 7 demo questions.
- Keeps the existing domain model and MCP output envelope — callers (`assistant-app`)
  need no change. The hexagonal boundary holds: the change is adapter + config only.
- v5 is the vendor's first long-term-stable version.

Trade-offs:

- Introduces a required secret (REST Countries API key, free tier 500 req/month),
  documented in README, `.env.example`, and the demo run log.
- Mapping now depends on the v5 nested shape; the not-found signal moved from HTTP
  404 to an empty result set, which the parser must handle explicitly.

## Alternatives Considered

- Stay on v3.1: rejected — permanently shut down.
- Drop `population` to match the docs' "no read" claim: rejected after live
  verification showed population is returned — dropping it would be an unnecessary
  domain change and would lose a fact the API provides.
- Switch to a different countries provider: rejected — v5 supports the full existing
  model with the smallest change; no reason to revisit ADR `0004`.
- Commit/share a single API key: rejected — violates `CLAUDE.md` §7 secrets rule.

## Verification

- `countries.mcp` config exposes a v5 base URL default and an optional API-key
  property; grep shows no hardcoded URL or key in business logic.
- With a valid key, `country_lookup` for Germany returns capital `Berlin`, region
  `Europe`, and a population, mapped into `CountryFacts`.
- With the key unset or invalid, the server still starts and the tool returns
  `SourceUnavailable` with an auth hint; no fact is fabricated.
- Unknown country (Atlantis) returns `NotFound` (empty `data.objects`).
- `./mvnw -pl countries-mcp-server test` passes; adapter tests cover v5 success,
  not-found (empty objects), unauthorized (401 envelope), and source-unavailable
  against a controlled HTTP stub (no live network, no real key in tests).
- README, `.env.example`, and the final demo evidence document the new key without exposing it.
