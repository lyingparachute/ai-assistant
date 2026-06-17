# ADR 0008: Migrate countries-mcp-server from REST Countries v3.1 to v5

## Status

Proposed — one sub-decision (population field) open, see "Decision".

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

v3.1 is permanently dead, not transiently unavailable. This blocks four of the six
Phase 7 demo questions (Germany capital, Munich-via-capital weather, combined, and
Berlin place-synthesis); each routes through a live country fact. The CDQ product
question is unaffected (it uses RAG).

The vendor's successor is v5. The following were confirmed against the live REST
Countries documentation and a live unauthenticated probe on 2026-06-17:

- Base URL: `https://api.restcountries.com/countries/v5`.
- Name lookup: `GET /names.common/{value}?response_fields=...` (property-path endpoint).
- Capital lookup: `GET /capitals/{value}` (plural; replaces v3.1 `/capital/{value}`).
- Auth required: `Authorization: Bearer {API_KEY}` (v3.1 was unauthenticated). A
  missing/invalid key returns HTTP 401 with an error envelope
  `{"errors":[{"message":"Authorization key required."}]}`, distinct from the
  success body.
- Free tier: 500 requests/month.
- Field reference (confirmed): `region` is readable/searchable; `capitals` is an
  array of **objects** (`{name, coordinates, attributes}`), not strings; `names.*`
  readable. **`population` is marked "dynamic, no read, no search"** — it cannot be
  returned via `response_fields` or any read endpoint.
- NOT confirmed (the 401 blocks reading a success body without a key): the exact
  nesting of the success response JSON. This must be captured from a real
  authenticated response before the field mapping is locked.

The population constraint is load-bearing: `CountryFacts` currently *requires*
population, so a like-for-like v5 migration would make every lookup fail
"missing required fields". v5 cannot satisfy the current domain model.

## Decision

Migrate `countries-mcp-server` to REST Countries v5, keeping REST Countries as the
provider, with these locked decisions:

- Base URL default becomes the v5 URL; name lookup uses `/names.common/{value}`,
  capital lookup uses `/capitals/{value}`. Both via typed `countries.mcp` config,
  no literals in business logic.
- The adapter sends `Authorization: Bearer {API_KEY}`. The key comes from config
  bound to `COUNTRIES_MCP_REST_COUNTRIES_API_KEY`. It is a secret: never committed,
  logged, or defaulted to a literal.
- The API key is **optional config** (no `@NotBlank` startup gate). When absent,
  blank, or rejected (401/403), the adapter returns the existing
  `RestCountriesQueryResult.SourceUnavailable` with an auth-specific hint — a named
  source failure, never a fabricated answer and never a server boot failure. This
  resolves the contradiction between fail-fast validation and the
  graceful-source-unavailable contract in favour of the latter.
- Capital is read from `capitals[]` objects (preferring `attributes.primary` when
  present, else the first entry), not from a string array.
- The MCP tool contract (`country_lookup` name, input schema) is unchanged.

**Open sub-decision — population.** Because v5 cannot return population, one of two
paths must be chosen before implementation (see
`docs/plans/fix-countries-rest-v5-migration.md`):

- **A (recommended): drop `population`** from `CountryFacts`, the assistant-side
  `CountryInfo`, the `country_lookup` JSON output, `CountryInfoResponse`, and the
  "Population:" answer line. Honest to what v5 provides; smallest steady-state
  surface. Cost: a cross-module domain change (both modules + MCP output contract +
  tests). The demo questions ask for capital, not population.
- **B: switch to a different free countries source** that returns population
  without a key. Keeps the domain model and avoids a new secret, but changes the
  documented source (revisits ADR `0004`) and is larger, riskier external work.

Option A is recommended for a recruitment-scope demo. The output-envelope
"no change" goal yields to honesty: an envelope that always claims a population it
cannot fetch would be worse than removing the field.

## Consequences

Benefits:

- Restores live country facts, unblocking four Phase 7 demo questions.
- v5 is the vendor's first long-term-stable version.

Trade-offs:

- Introduces a required secret (REST Countries API key, free tier 500 req/month).
  Documented in README, `.env.example`, and the demo run log.
- Option A is a deliberate domain change touching both modules and the MCP output
  envelope; the "adapter + config only" framing does **not** hold.
- The v5 success-body nesting must be re-verified against a live authenticated
  response before the mapping is finalized.

## Alternatives Considered

- Stay on v3.1: rejected — permanently shut down.
- Keep population by a second v5 call/endpoint: rejected — v5 marks population
  unreadable everywhere; no second endpoint exposes it. Over-engineered.
- Commit/share a single API key: rejected — violates `CLAUDE.md` §7 secrets rule.

## Verification

- `countries.mcp` config exposes a v5 base URL default and an optional API-key
  property; grep shows no hardcoded URL or key in business logic.
- With a valid key, `country_lookup` for Germany returns capital `Berlin` with
  region populated (and population per the chosen option).
- With the key unset or invalid, the tool returns `SourceUnavailable` with an auth
  hint, the server still starts, and no fact is fabricated.
- Unknown country (Atlantis) returns `NotFound`.
- `./mvnw -pl countries-mcp-server test` passes; adapter tests cover v5 success,
  not-found, unauthorized (401 error envelope), and source-unavailable against a
  controlled HTTP stub (no live network, no real key in tests).
- README, `.env.example`, and `docs/demo/demo-run-log.md` document the new key.
