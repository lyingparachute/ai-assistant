# ExecPlan — countries-mcp-server REST Countries v5 migration

Status: proposed — blocked on the population sub-decision (no code until approved)
Owner: TBD
Source: ADR `0008`, demo blocker recorded in `docs/demo/demo-run-log.md`
Scope module: `countries-mcp-server` primarily; `assistant-app` if option A (drop population) is chosen.

## Why now

REST Countries v3.1 is permanently deprecated; every request returns a deprecation
error (re-verified 2026-06-17). `countries-mcp-server` cannot resolve any country
fact, which blocks four of the six Phase 7 demo questions
(`docs/plans/phase-7-demo-capture.md`): Germany capital, Munich weather via the
capital of Germany, the combined question, and Berlin place-synthesis. Phase 7
cannot reach Done until country lookups return real data. This is a vendor-caused
regression in landed Phase 2 code, not new feature work.

## Decision required before implementation

v5 cannot return `population` (field reference: "dynamic, no read, no search",
confirmed 2026-06-17). `CountryFacts` currently requires population, so a v5
migration forces one of:

- **A (recommended): drop `population`.** Remove it from `CountryFacts`
  (`countries-mcp-server/.../model/CountryFacts.java`), the MCP output
  (`.../tools/CountryToolResult.java`), the assistant-side `CountryInfo`
  (`assistant-app/.../tools/CountryInfo.java`), `CountryInfoResponse`,
  `ChatHttpMapper`, the `CountriesMcpResponseMapper` parse
  (`assistant-app/.../adapters/outbound/mcp/CountriesMcpResponseMapper.java:80,91,100`),
  and the "Population: ..." line (`AnswerQuestionUseCase.java:272`). Cross-module
  domain change + MCP output-envelope change + test updates.
- **B: switch countries provider** to one returning population without a key.
  Revisits ADR `0004`; larger, riskier; out of this plan as written.

This plan assumes **A** below. If B is chosen, this plan is replaced.

## Target state (option A)

`countries-mcp-server` resolves country facts through REST Countries **v5**:

- Outbound adapter targets `https://api.restcountries.com/countries/v5`, sends
  `Authorization: Bearer {API_KEY}`, parses the v5 JSON:API body into a
  population-free `CountryFacts` (name, capital, region).
- `country_lookup` tool name and input schema unchanged; output envelope drops the
  `population` key (the one intentional contract change, per ADR `0008`).
- Base URL and API key come from typed `countries.mcp` config backed by env vars.

## Scope

- `RestCountriesHttpAdapter`
  (`countries-mcp-server/.../restcountries/RestCountriesHttpAdapter.java`):
  - `findByName` → `/names.common/{value}?response_fields=...`;
    `findByCapital` → `/capitals/{value}?response_fields=...` (current code uses
    `/name/` and `/capital/` at lines 42/47; `FIELDS_QUERY` at line 24 changes to
    the v5 `response_fields` form).
  - `Authorization: Bearer` header on every request.
  - Replace v3.1 array parsing (`mapResponse`/`mapCountry`, lines 67-106) with v5
    `data.objects[]` parsing. Read capital from `capitals[]` objects
    (prefer `attributes.primary`, else first), not string index 0 (current line
    98-100 reads strings). Map into population-free `CountryFacts`.
  - Outcomes: keep `Success`/`NotFound`/`SourceUnavailable`. Parse the v5 error
    envelope `{"errors":[{"message":...}]}`; 401/403 → `SourceUnavailable` with an
    auth hint; other non-2xx and parse failures → `SourceUnavailable`; 404/empty
    `data.objects` → `NotFound`.
- `CountriesMcpConfiguration`
  (`countries-mcp-server/.../config/CountriesMcpConfiguration.java`): change the
  `restCountriesBaseUrl` default to v5; add an **optional** API-key property (no
  `@NotBlank` — a blank key must not fail startup; it surfaces as
  `SourceUnavailable` at request time).
- `application.yml` (countries-mcp-server): bind v5 base URL and the API key from
  `COUNTRIES_MCP_REST_COUNTRIES_API_KEY`.
- `assistant-app` MCP wiring: the host key must reach the countries subprocess.
  `StdioMcpToolInvoker` currently hardcodes passthrough to the **weather** tool only
  (`WEATHER_ENV_PASSTHROUGH` at line 31, gated on tool name `get-weather`). There is
  **no generic passthrough** to mirror. Choose one and state it in the PR:
  generalize the passthrough to a per-tool configured env-key list, OR add an
  explicit countries passthrough entry. Putting the literal key in
  `assistant-app/application.yml` is rejected (secret-in-repo risk).
- Domain/contract changes for option A: drop `population` across the files listed in
  "Decision required".
- Tests: adapter tests for v5 success, not-found, unauthorized (401 envelope), and
  source-unavailable against a controlled HTTP stub; update countries + assistant
  tests that assert population.
- Docs: README prerequisites, `.env.example`, and `docs/demo/demo-run-log.md` note
  the new key.

## Out of scope

- Provider switch (option B) — separate decision/plan.
- Any change to the MCP tool **name** or **input schema**.
- `assistant-app` orchestration/routing/domain changes beyond removing population
  and the subprocess env wiring.
- Phase 7 demo capture itself (separate plan).

## Invariants

- Hexagonal boundary: HTTP/v5 details stay in the outbound adapter and config; the
  `RestCountriesPort` interface is unchanged. (Note: option A intentionally changes
  the `CountryFacts` shape — a domain change, not a boundary leak.)
- No `null` returns; outcomes stay the sealed `RestCountriesQueryResult`.
- No fabricated facts: missing key, auth failure, or any non-2xx → `SourceUnavailable`.
- Secrets: the API key is never committed, logged, or defaulted to a literal.
- The server starts whether or not the key is set.

## Risks and open questions

- **OPEN: v5 success-body nesting.** The 401 blocks reading a success body without a
  key. Capture a real authenticated response (M1) before locking the field mapping.
  Do not guess the JSON shape.
- **Secret + rate limit:** implementation/live verification need a v5 key (free, 500
  req/month). Keep live calls minimal; rely on stubbed adapter tests.
- **Blast radius (option A):** population removal touches both modules, the MCP
  output envelope, the HTTP response, and tests. Confirm every consumer listed in
  "Decision required" is updated.

## Definition of Done (option A)

- [ ] Adapter targets v5 base URL, uses `/names.common/{value}` and `/capitals/{value}`,
      sends `Authorization: Bearer`; base URL and key from typed config/env, no
      literals (grep clean).
- [ ] With a valid key, `country_lookup` for Germany returns capital `Berlin` with
      region populated (paste live tool output).
- [ ] With the key unset or invalid, the server still starts and the tool returns
      `SourceUnavailable` with an auth hint, no fabricated fact (paste output).
- [ ] Unknown country (Atlantis) returns `NotFound` (paste output).
- [ ] `population` removed from all consumers listed above; no compile/test
      reference remains (grep clean).
- [ ] `./mvnw -pl countries-mcp-server test` and `./mvnw -pl assistant-app test`
      pass; adapter tests cover v5 success, not-found, unauthorized, source-unavailable
      against a controlled stub (paste reactor summaries).
- [ ] `country_lookup` tool name + input schema unchanged (diff shows only the
      output `population` key removed).
- [ ] README, `.env.example`, and `docs/demo/demo-run-log.md` document the new key.
- [ ] Open question (success-body nesting) resolved with evidence from a live v5
      response.

## Milestones

- [ ] M1 — Capture a live authenticated v5 response; confirm success-body nesting;
      record in implementation-notes.
- [ ] M2 — Adapter + config migration + population removal (countries-mcp-server)
      with stubbed adapter tests green.
- [ ] M3 — `assistant-app` population removal + subprocess env wiring + live
      `country_lookup` verification.
- [ ] M4 — README + `.env.example` + demo-run-log doc updates.
