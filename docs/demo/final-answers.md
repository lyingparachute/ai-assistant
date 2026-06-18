# Final Demo Answers

Captured from the running assistant on **2026-06-17** (local stack). Raw responses:
`docs/demo/capture/*.json`. Traces: `docs/demo/request-traces/`. Volatile values
(temperatures) are real observations labelled with retrieval time.

Every answer below was produced by the assistant exercising the named source path. No answer
is hand-written; none is fabricated. Where a source path cannot produce a fact, the assistant
returns a source-unavailable / no-route response rather than guessing.

> The earlier 2026-06-16 capture was blocked (REST Countries v3.1 deprecated, weather key
> unset, RAG not ingested). All three are now resolved — v3.1→v5 migration (ADR `0008`),
> weather key configured, RAG ingested — and the run below is fully grounded.

## Local configuration summary

| Setting | Value |
| --- | --- |
| Assistant API | `http://localhost:8080` |
| Ollama chat model | `qwen3:4b` |
| Ollama embedding model | `nomic-embed-text` |
| pgvector | Docker `pgvector/pgvector:pg17`, database `assistant_rag` |
| Countries MCP | REST Countries **v5** (`https://api.restcountries.com/countries/v5`, Bearer key) |
| Weather MCP | `scripts/mcp-weather` → semdin/mcp-weather → WeatherAPI.com |
| RAG | CDQ Fraud Guard product page ingested into pgvector |

## 1. Country fact — `countries` (REST Countries v5 via MCP)

**Q:** What is the capital city of Germany?
**A:** The capital of Germany is Berlin.
**Source:** `countries_facts: USED` — `{ country: Germany, capital: Berlin, region: Europe,
population: 83,497,147 }`.
**Trace:** `request-traces/01-germany-capital.txt` (route `COUNTRY_CAPITAL`, `CountriesPort`).

## 2. Weather observation — `weather` (WeatherAPI via MCP)

**Q:** What is the temperature currently in Munich?
**A:** The current temperature in Munich is **23.2°C** (retrieved 2026-06-17T08:28:01Z).
**Source:** `weather_observation: USED` — location Munich, retrieval time labelled.
**Trace:** `request-traces/02-munich-weather.txt` (route `WEATHER_LOCATION`, `WeatherPort`).

## 3. Combined country + weather — `combined`

**Q:** What is the temperature of the capital of Germany currently?
**A:** The current temperature in Berlin, the capital of Germany, is **19.1°C**
(retrieved 2026-06-17T08:28:02Z).
**Source:** `countries_facts: USED` (Germany → Berlin) then `weather_observation: USED`
(Berlin). Two sources, ordered.
**Trace:** `request-traces/03-germany-capital-weather.txt` (route `COUNTRY_THEN_WEATHER`).

## 4. Place synthesis — `place-synthesis`

**Q:** What do you know about Berlin?
**A:** Berlin is the capital of Germany, located in Europe, with a population of 83,497,147.
**Source:** `countries_facts: USED` (grounding fact) + `model_synthesis: USED` (phrasing).
Synthesis is labelled as synthesis, never as a tool result.
**Trace:** `request-traces/04-berlin-place.txt` (route `PLACE_SYNTHESIS`).

## 5. Product knowledge showcase — `cdq-product` (RAG over CDQ Fraud Guard)

**Q:** What is CDQ Fraud Guard?
**A:** CDQ Fraud Guard is a service that manages and verifies global payment data to combat
payment fraud and inaccurate bank account information. It identifies bank accounts that do
not belong to the declared business partner but to an attacker, by leveraging
community-shared data on proven bank accounts and known fraud cases. Highlights include
bank-account verification, customizable trust scores, real-time fraud alerts, fraud-case
management, and API integration with existing financial systems.
**Source:** `rag_knowledge: USED` — 5 retrieved snippets from
`https://www.cdq.com/products/cdq-fraud-guard` (top similarity 0.87) + `model_synthesis: USED`.
RAG is grounding-only; synthesis is labelled.
**Trace:** `request-traces/05-cdq-product.txt` (`ragRetrievalCount` > 0).

## 6. Source-unavailable / honesty — `source-unavailable`

**Q:** What is the capital city of Atlantis?
**A:** I cannot answer this question: No matching source route for this question.
**Source:** none — the deterministic honesty guard refuses rather than answering a
non-existent country from model memory.
**Trace:** `request-traces/06-source-unavailable.txt` (no sources; no fabrication).

> This demonstrates the no-fabrication guarantee via the routing guard. A *source-tried-then-
> unavailable* path (e.g. weather key unset → `weather_observation: UNAVAILABLE`) is also
> covered by the source-unavailable tests in the suite.

## Verification

- E2E demo verification against the live stack: `./mvnw -pl e2e-tests verify -P e2e` →
  `RequiredDemoQuestionsIT` Tests run: 5, Failures: 0, Errors: 0. BUILD SUCCESS.
- Module suites green: countries-mcp-server 30, assistant-app 223.
