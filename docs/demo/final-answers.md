# Final Demo Answers

Captured from the running assistant on **2026-06-18T08:02:27Z** (local stack, SSE transport).
Traces: `docs/demo/request-traces/`.
Volatile values such as weather are labelled with retrieval time.

Every answer below was produced by the assistant exercising the named source path. No answer is
hand-written. Where a source path cannot produce a fact, the assistant returns a source-unavailable
or no-route response rather than guessing.

## Local Configuration Summary

| Setting | Value |
| --- | --- |
| Assistant API | `http://localhost:8080` |
| Chat Interface | `http://localhost:4321` |
| Ollama chat model | `qwen3:4b` |
| Ollama embedding model | `nomic-embed-text` |
| pgvector | Docker `pgvector/pgvector:pg17`, database `assistant_rag` (existing 9 chunks) |
| Countries MCP | REST Countries v5 (`https://api.restcountries.com/countries/v5`, Bearer key) |
| Weather MCP | `scripts/mcp-weather` -> semdin/mcp-weather -> WeatherAPI.com |
| RAG | CDQ Fraud Guard product page already ingested into pgvector |

## 1. Country Facts

**Q:** What is the capital city of Germany?
**A:** The capital of Germany is Berlin.
**Source:** `countries_facts: USED` - Germany, capital Berlin, region Europe, population 83,497,147.
**Trace:** `request-traces/01-germany-capital.txt` (`COUNTRY_CAPITAL`, `CountriesPort`, SSE `trace` + `final`, no `token` events).

## 2. Weather Observation

**Q:** What is the temperature currently in Munich?
**A:** The current temperature in Munich is **24.3 C**. Retrieved: `2026-06-18T08:02:28.177905Z`.
**Source:** `weather_observation: USED` - location Munich, retrieval time labelled.
**Trace:** `request-traces/02-munich-weather.txt` (`WEATHER_LOCATION`, `WeatherPort`).

## 3. Combined Country Facts and Weather Observation

**Q:** What is the temperature of the capital of Germany currently?
**A:** The current temperature in Berlin, the capital of Germany, is **23.4 C**. Retrieved: `2026-06-18T08:02:28.645675Z`.
**Source:** `countries_facts: USED` (Germany -> Berlin), then `weather_observation: USED` (Berlin).
**Trace:** `request-traces/03-germany-capital-weather.txt` (`COUNTRY_THEN_WEATHER`).

## 4. Place Synthesis

**Q:** What do you know about Berlin?
**A:** Berlin is the capital of Germany. Berlin is the capital of Germany. It is in Europe. The population is83497147.
**Source:** `countries_facts: USED` + `model_synthesis: USED`. Synthesis is labelled as synthesis, never as a tool result.
**Trace:** `request-traces/04-berlin-place.txt` (`PLACE_SYNTHESIS`, SSE token events before `final`).

## 5. Product Knowledge Showcase

**Q:** What is CDQ Fraud Guard?
**A:** CDQ Fraud Guard is a service that manages and verifies global payment data to prevent payment fraud and ensure compliance. It identifies fraudulent bank accounts and protects businesses by leveraging a shared database of validated accounts and known fraud cases. Key features include bank account verification, customizable trust scores based on transaction volume, real-time fraud alerts, fraud case management, and seamless integration with existing financial systems via a robust API. This service reduces fraud risk, streamlines business partner onboarding, and provides peace of mind for secure transactions.
**Source:** `rag_knowledge: USED` - 5 retrieved snippets from `https://www.cdq.com/products/cdq-fraud-guard` (top similarity 0.8711) + `model_synthesis: USED`.
**Trace:** `request-traces/05-cdq-product.txt` (`ragRetrievalCount` = 5).

## 6. Source-Unavailable / Honesty Guard

**Q:** What is the capital city of Atlantis?
**A:** I cannot answer this question: No matching source route for this question.
**Source:** none - deterministic routing refuses to answer a non-existent country from model memory.
**Trace:** `request-traces/06-source-unavailable.txt` (`UNSUPPORTED`, no ports invoked).

## Verification

- Demo capture: `./scripts/capture-demo-answers.sh` -> exit 0 at `2026-06-18T08:02:27Z`; six SSE terminal `final` responses written to numbered JSON files.
- E2E demo verification: `./mvnw verify -P e2e` -> `RequiredDemoQuestionsIT` Tests run: 5, Failures: 0, Errors: 0, Skipped: 0; `BUILD SUCCESS`.
- RAG re-ingest did not complete in this run: the README command failed as a Maven lifecycle-phase error, and the `ASSISTANT_INGEST_RAG=true` entry point failed in the `ingest-rag` profile. Existing pgvector chunks were verified (`rag_chunks` count = 9), and the live CDQ answer retrieved 5 snippets from them.
- Clean-checkout command table: `docs/demo/clean-checkout-verification.md`.
