# ExecPlan — countries-mcp-server REST Countries v5 migration

Status: landed — 57fbd9b (2026-06-17, adapter + config only, population retained; live-verified end-to-end)
Owner: TBD
Source: ADR `0008`, demo blocker recorded in `docs/demo/demo-run-log.md`
Scope module: `countries-mcp-server` only.

## Why now

REST Countries v3.1 is permanently deprecated; every request returns a deprecation
error (re-verified 2026-06-17). `countries-mcp-server` cannot resolve any country
fact, which blocks four of the six Phase 7 demo questions
(`docs/plans/phase-7-demo-capture.md`): Germany capital, Munich weather via the
capital of Germany, the combined question, and Berlin place-synthesis. Phase 7
cannot reach Done until country lookups return real data. This is a vendor-caused
regression in landed Phase 2 code, not new feature work.

## Verified v5 contract (live, with a real key, 2026-06-17)

The live authenticated response is authoritative over the public docs (the docs'
field reference wrongly claims `population` is "no read"; the live API returns it):

- Base: `https://api.restcountries.com/countries/v5`
- Name lookup: `GET /names.common/{value}?response_fields=names.common,capitals,region,population`
- Capital lookup: `GET /capitals/{value}?response_fields=names.common,capitals,region,population`
- Auth: `Authorization: Bearer {key}`; missing/invalid → HTTP 401
- Success body (nested):
  `data.objects[].names.common`, `data.objects[].region`,
  `data.objects[].population` (number), `data.objects[].capitals[]` = objects with
  `name` and `attributes.primary`
- Not found → HTTP **200** with `data.objects: []`

All four `CountryFacts` fields are available, so the domain model is unchanged.

## Target state

`countries-mcp-server` resolves country facts through REST Countries **v5** with
`CountryFacts` (name, capital, region, population) unchanged. Outbound-adapter and
configuration change only; the `RestCountriesPort`, domain model, MCP tool name,
input schema, and output JSON envelope are untouched. `assistant-app` needs no change.

## Scope

- `RestCountriesHttpAdapter`
  (`countries-mcp-server/.../restcountries/RestCountriesHttpAdapter.java`):
  - `findByName` → `/names.common/{value}`; `findByCapital` → `/capitals/{value}`
    (current code uses `/name/` and `/capital/` at lines 42/47). `FIELDS_QUERY`
    (line 24) becomes
    `response_fields=names.common,capitals,region,population`.
  - Add `Authorization: Bearer` header (key from config) on every request.
  - Replace v3.1 array parsing (`mapResponse`/`mapCountry`, lines 67-106) with v5
    nested parsing: read `names.common`, `region`, `population` from
    `data.objects[]`, and the capital from `capitals[]` objects (prefer
    `attributes.primary`, else first) — current line 98-100 reads capital as a
    string array and must change.
  - Outcomes unchanged set: empty `data.objects` → `NotFound` (v5 returns HTTP 200,
    not 404, so the current 404 check is replaced by an empty-result check);
    401/403 → `SourceUnavailable` with an auth hint; other non-2xx and parse
    failures → `SourceUnavailable`. Parse the v5 error envelope
    `{"errors":[{"message":...}]}` distinctly from the success body.
  - `CountryFacts` still requires all four fields; a v5 object missing any →
    `SourceUnavailable("missing required country fields")`, as today.
- `CountriesMcpConfiguration`
  (`countries-mcp-server/.../config/CountriesMcpConfiguration.java`): change the
  `restCountriesBaseUrl` default to the v5 URL; add an **optional** API-key property
  (no `@NotBlank` — a blank key must not fail startup; it surfaces as
  `SourceUnavailable` at request time).
- `application.yml` (countries-mcp-server): bind the v5 base URL and the API key from
  env `REST_COUNTRIES_API_KEY`.
- `assistant-app` `application.yml`: update the countries subprocess env
  `COUNTRIES_MCP_REST_COUNTRIES_BASE_URL` to the v5 base URL and forward the host
  `REST_COUNTRIES_API_KEY` to the countries subprocess. `StdioMcpToolInvoker`
  currently hardcodes env passthrough to the **weather** tool only
  (`WEATHER_ENV_PASSTHROUGH`, line 31, gated on tool name `get-weather`); there is no
  generic mechanism. Either generalize the passthrough to a per-tool configured
  env-key list, or add an explicit countries passthrough entry. Putting the literal
  key in `assistant-app/application.yml` is rejected (secret-in-repo risk). State the
  chosen approach in the PR. (This is the only `assistant-app` touch — wiring, not
  domain/orchestration.)
- Tests: adapter tests for v5 success, not-found (empty objects), unauthorized (401
  envelope), and source-unavailable against a controlled HTTP stub.
- Docs: README prerequisites, `.env.example`, and `docs/demo/demo-run-log.md` note
  the new key.

## Out of scope

- Provider switch (v5 supports the full model — no reason to revisit ADR `0004`).
- Any change to `CountryFacts`, the MCP tool name, input schema, or output envelope.
- Any `assistant-app` orchestration/routing/domain change beyond the subprocess env
  wiring above.
- Phase 7 demo capture itself (separate plan).

## Invariants

- Hexagonal boundary: v5/HTTP details stay in the outbound adapter and config;
  `RestCountriesPort`, `CountryFacts`, and the inbound MCP adapter are unchanged.
- No `null` returns; outcomes stay the sealed `RestCountriesQueryResult`.
- No fabricated facts: missing key, auth failure, or any non-2xx → `SourceUnavailable`.
- Secrets: the API key is never committed, logged, or defaulted to a literal.
- The server starts whether or not the key is set.

## Risks

- **Secret + rate limit:** live verification needs a v5 key (free, 500 req/month).
  Keep live calls minimal; rely on stubbed adapter tests.
- **Shape coupling:** mapping depends on the v5 nested shape and the empty-result
  not-found signal; both are covered by stub tests using the verified live body.

## Definition of Done

- [x] Adapter targets v5 base URL, uses `/names.common/{value}` and
      `/capitals/{value}` with the four-field `response_fields`, sends
      `Authorization: Bearer`; base URL and key from typed config/env, no literals
      (grep clean).
- [x] With a valid key, `country_lookup` for Germany returns capital `Berlin`,
      region `Europe`, and a population (paste live tool output).
- [x] With the key unset or invalid, the server still starts and the tool returns
      `SourceUnavailable` with an auth hint, no fabricated fact (paste output).
- [x] Unknown country (Atlantis) returns `NotFound` (paste output).
- [x] `./mvnw -pl countries-mcp-server test` passes; adapter tests cover v5 success,
      not-found, unauthorized, and source-unavailable against a controlled stub
      (paste reactor summary).
- [x] `CountryFacts`, `country_lookup` name, input schema, and output envelope
      unchanged (diff shows adapter + config only, plus assistant-app env wiring).
- [x] README, `.env.example`, and `docs/demo/demo-run-log.md` document the new key.

## Milestones

- [x] M1 — Adapter + config migration with stubbed adapter tests green.
- [x] M2 — `assistant-app` subprocess env wiring + live `country_lookup` verification.
- [x] M3 — README + `.env.example` + demo-run-log doc updates.
