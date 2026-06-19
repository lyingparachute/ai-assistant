# Final Demo Answers

Captured from the running Assistant API on **2026-06-18T15:41:34Z** (`http://localhost:8080`, SSE transport).
Traces: `docs/demo/request-traces/`.

Every answer below was produced by the assistant exercising the named source path. No answer is
hand-written. Where a source path cannot produce a fact, the assistant returns a source-unavailable
or no-route response rather than guessing.

## Local Configuration Summary

| Setting | Value |
| --- | --- |
| Assistant API | `http://localhost:8080` |
| Chat Interface | Not running during this recapture; port `4321` was free |
| Ollama chat model | `qwen3:4b` |
| Ollama embedding model | `nomic-embed-text` |
| pgvector | Docker `pgvector/pgvector:pg17`, database `assistant_rag` |
| Countries MCP | REST Countries v5 (`https://api.restcountries.com/countries/v5`, bearer key configured) |
| Weather MCP | `scripts/mcp-weather` -> semdin/mcp-weather -> WeatherAPI.com; tool call timed out in this run |
| RAG | CDQ Fraud Guard product page ingested into pgvector; latest ingestion outcome `UNCHANGED`, 9 chunks |

## 1. Country Facts

**Q:** What is the capital city of Germany?
**A:** The capital of Germany is Berlin.
**Source:** `countries_facts: USED` - Germany, capital Berlin, region Europe, population 83,497,147.
**Trace:** `request-traces/01-germany-capital.txt` (`COUNTRY_CAPITAL`, `CountriesPort`, SSE `trace` + `final`, no `token` events).

## 2. Weather Observation

**Q:** What is the temperature currently in Munich?
**A:** Weather MCP is unavailable: MCP tool call failed for get-weather.
**Source:** `weather_observation: UNAVAILABLE` - no temperature was invented.
**Trace:** `request-traces/02-munich-weather.txt` (`WEATHER_LOCATION`, `WeatherPort`, MCP timeout).
**Blocker:** Local weather MCP call timed out after 60 seconds. Controlled tests verify successful weather mapping; this live run does not prove current Munich temperature.

## 3. Combined Country Facts and Weather Observation

**Q:** What is the temperature of the capital of Germany currently?
**A:** The capital of Germany is Berlin. Weather for Berlin is unavailable: MCP tool call failed for get-weather.
**Source:** `countries_facts: USED` (Germany -> Berlin), then `weather_observation: UNAVAILABLE`.
**Trace:** `request-traces/03-germany-capital-weather.txt` (`COUNTRY_THEN_WEATHER`, countries before weather).
**Blocker:** Local weather MCP call timed out after the capital was resolved. No Berlin temperature was invented.

## 4. Place Synthesis

**Q:** What do you know about Berlin?
**A:** Berlin is the capital of Germany. Berlin is the capital of Germany, a country in Europe with a population of83,497,147.
**Source:** `countries_facts: USED` + `model_synthesis: USED`. Synthesis is labelled as synthesis, never as a tool result.
**Trace:** `request-traces/04-berlin-place.txt` (`PLACE_SYNTHESIS`, countries and LLM only).

## 5. Product Knowledge Showcase

**Q:** What is CDQ Fraud Guard?
**A:** CDQ Fraud Guard is a service that manages and verifies global payment data to combat fraud by leveraging community-shared data on proven bank accounts and known fraud cases. It helps businesses identify bank accounts that do not belong to the declared business partner but to attackers, and provides features including bank account verification, customizable trust scores based on transaction volume, real-time fraud alerts, and fraud case management.
**Source:** `rag_knowledge: USED` - 5 retrieved snippets from `https://www.cdq.com/products/cdq-fraud-guard` (top similarity 0.8711) + `model_synthesis: USED`.
**Trace:** `request-traces/05-cdq-product.txt` (`ragRetrievalCount` = 5).

## 6. Source-Unavailable / Honesty Guard

**Q:** What is the capital city of Atlantis?
**A:** I cannot answer this question: No matching source route for this question.
**Source:** none - deterministic routing refuses to answer a non-existent country from model memory.
**Trace:** `request-traces/06-source-unavailable.txt` (`UNSUPPORTED`, no ports invoked).

## Verification

- Demo capture: `./scripts/capture-demo-answers.sh` -> exit 0 at `2026-06-18T15:41:34Z`; six SSE terminal `final` responses written to local, gitignored numbered JSON files under `docs/demo/capture/`, with submitted excerpts in this document and `docs/demo/request-traces/`.
- E2E demo verification: `./mvnw verify -P e2e` -> `RequiredDemoQuestionsIT` Tests run: 5, Failures: 0, Errors: 0, Skipped: 0; `BUILD SUCCESS`.
- RAG ingestion: both `ASSISTANT_INGEST_RAG=true ./mvnw -pl assistant-app spring-boot:run` and `./mvnw -pl assistant-app spring-boot:run -Dspring-boot.run.arguments=--ingest-rag` completed with `chunk-count: 9`, `outcome: UNCHANGED`.
- Weather live values are blocked in this capture by the weather MCP timeout; the assistant returned source-unavailable responses and did not substitute temperatures.
- Clean-checkout command table: `docs/demo/clean-checkout-verification.md`.
