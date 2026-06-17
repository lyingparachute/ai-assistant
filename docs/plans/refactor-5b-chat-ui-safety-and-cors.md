# ExecPlan — Chat UI escaping safety, CORS typing & env documentation

Status: draft — round-1 + round-2 critic reviewed; round-2 blocker resolved in the addendum below
Owner: TBD
Source: docs/reviews/2026-06-16-code-quality-audit.md (H-9, H-10, H-8, H-11)
Scope: `chat-ui/` (index.astro, sourceDisplay.ts, api.ts, .env.example) + `assistant-app`
`config/AssistantCorsProperties` + `HttpInboundConfiguration`. Independent of refactor-1/5a. Round-1 resolutions
marked [R1: Pn-x].

## Why now

The UI assembles result HTML as strings and assigns `innerHTML` with a hand-rolled `escapeHtml`, an
escaping-by-convention pattern that is fragile to future edits; CORS allows a `GET` method with no GET endpoint
and an inline `allowedHeaders("*")` rather than typed config; the API base URL default is an undocumented magic
literal. These are contained, frontend-leaning hygiene items with a small Java-config slice.

## Problem (verified against source)

1. **innerHTML assembly (H-9)** [R1: P5-4]: `index.astro:75` assigns `sources.innerHTML = renderSources(...)`;
   `sourceDisplay.ts` (~103 lines) builds HTML strings routing every interpolated value through a hand-rolled
   `escapeHtml`. Currently every value IS escaped and `escapeHtml` is applied consistently, BUT it omits `'`
   (single quote) and the pattern is escaping-by-convention — a future field added without `escapeHtml` is a
   stored-XSS via the assistant's own response. **Contained risk, not actively exploitable today** (no value
   sits in a single-quoted attribute).
2. **CORS (H-10)** [R1: P5-5]: `AssistantCorsProperties` holds only `allowedOrigins`; `HttpInboundConfiguration`
   hardcodes `allowedMethods("GET","POST","OPTIONS")` and `allowedHeaders("*")` inline. The contract (spec
   14:147-155) needs only POST + preflight for `/api/chat`; GET is dead permission surface.
3. **`DEFAULT_API_URL` magic literal (H-8)**: `api.ts:3` hardcodes `http://localhost:8080` as a silent fallback;
   undocumented.
4. **Response DTO indirection (H-11)**: `CountryInfoResponse`/`WeatherReportResponse`/`KnowledgeSnippetResponse`
   mirror domain 1:1 — **intentionally kept** (wire decoupling); only document the decision.

## Target state (decisions locked)

- **UI escaping (contained option, locked) [R1: P5-4]**: keep the string-template approach but (a) fix
  `escapeHtml` to also escape `'`; (b) add a test feeding `<script>`, `"`, `'`, `&` payloads through each render
  function asserting no raw injection; (c) add a one-line comment marking the single `innerHTML` site as the
  trust boundary and that all interpolation must go through `escapeHtml`. The full `createElement`/`textContent`
  rewrite is **not** chosen (smallest-design rule; the contained fix removes the bug class for the recruitment-
  task scope). The "XSS-prone-by-construction" framing is downgraded to "escaping-by-convention, contained".
- **CORS (locked) [R1: P5-5]**: this is **adding** typed `allowedMethods` and `allowedHeaders` to
  `AssistantCorsProperties` (with documented defaults) — not "moving" existing fields. Remove the inline literals
  from `HttpInboundConfiguration`; **drop `GET`** (no GET endpoint); keep POST + OPTIONS. Add a test/assertion
  that the bound policy yields POST+OPTIONS and not GET.
- **`DEFAULT_API_URL`**: document the local default in `.env.example` and a code comment; keep the env-var path
  (`PUBLIC_ASSISTANT_API_URL`) as the configured source.
- **Response DTOs**: add a one-line comment/ADR note recording the deliberate wire-decoupling indirection; do
  not delete them (deletion would leak domain types to the wire).

## Non-goals

- No change to the request/response JSON contract or to CORS origins behavior beyond removing the unused GET.
- No UI features; no DTO deletion; no `createElement` rewrite.

## Invariants to preserve

- No raw (unescaped) assistant-response value reaches `innerHTML`.
- CORS still permits the documented `/api/chat` POST + preflight.
- `chat-ui` builds cleanly.

## Risks / open questions

- `escapeHtml` change must not double-escape already-safe content; the payload test is the guard.
- Slices: (1) UI escaping fix + payload test + comment; (2) CORS typed properties + drop GET + test; (3)
  `.env.example` doc + DTO decision note.

## Definition of Done (binary)

- [ ] `escapeHtml` escapes `&`, `<`, `>`, `"`, `'`; a test feeds an XSS payload through every render function and
      asserts the output contains no executable markup.
- [ ] `AssistantCorsProperties` has typed `allowedMethods`/`allowedHeaders`; `HttpInboundConfiguration` has no
      inline method/header literals; CORS yields POST+OPTIONS, not GET (asserted).
- [ ] `DEFAULT_API_URL` documented in `.env.example`; response-DTO indirection decision recorded in a comment/ADR.
- [ ] `cd chat-ui && npm run build` succeeds. [R1: P5-9] (No `astro check` — the project has no such script/dep.)
- [ ] `./mvnw -o test` BUILD SUCCESS.

## Round-2 critic resolutions (authoritative)

- **[P5B-3, blocking] CORS assertion mechanism locked.** "Asserted" is replaced by a concrete verification: a
  MockMvc preflight `OPTIONS /api/chat` with `Access-Control-Request-Method: POST` returns an
  `Access-Control-Allow-Methods` containing POST and OPTIONS and NOT GET; a preflight requesting GET is rejected.
  (Proves the bound CORS policy, not merely that a property list exists.)
- **[P5B-1] Test surface**: `escapeHtml` is private; the payload test drives the sole export `renderSources`
  across each `source.type` variant + the unavailable path, asserting no executable markup and `'` → `&#39;`.
- **[P5B-2] `.env.example` already exists** with `PUBLIC_ASSISTANT_API_URL`. The remaining work is a comment in
  `api.ts` noting `DEFAULT_API_URL` is the local fallback and the env var is the configured source; DoD reworded.
- **[P5B-4] Header-default decision**: keep `allowedHeaders` default `["*"]` for the local demo (documented), OR
  tighten to `["Content-Type"]` (the only header sent). Decision: tighten to `["Content-Type"]` — same rationale
  as dropping GET (no broad permission surface). State it in the typed property default.
