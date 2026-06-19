# Recruitment Readiness Audit

Audit date: 2026-06-18
Git SHA verified: `0fc96c22b6d4ddcc30c04b95d84a5121acbe81ec`
Machine context: macOS local checkout, Java `21.0.9`, Node `v26.3.0`, npm `11.16.0`, Docker `29.5.2`, Ollama local models `qwen3:4b` and `nomic-embed-text:latest`

## Executive Summary

The repository is close to recruitment-ready, but the latest live evidence is not a clean full demo because the weather MCP call timed out during demo capture. Core Java tests, Maven verification, Chat Interface tests/build, countries packaging, RAG ingestion, and E2E route checks pass locally. The assistant handles unavailable weather honestly, but a recruiter expecting current temperatures for Munich and Berlin would see source-unavailable answers until the weather MCP path is fixed or re-captured successfully. Documentation has been refreshed to match the verified state instead of overstating the live demo.

## Completion Matrix

| ID | Requirement | Status | Evidence | Gap / Fix If Not PASS |
|---|---|---:|---|---|
| AC-01 | Natural-language question accepted through Chat Interface/API | PASS | `./mvnw verify -P e2e` passed; `RequiredDemoQuestionsIT` 5 tests, 0 failures; `docs/spec/14-assistant-api-contract.md:31` defines `POST /api/chat` | None |
| AC-02 | Session display appends user/assistant turns | PASS | `chat-ui/src/lib/sessionDisplay.ts:26`; `chat-ui/src/lib/sessionDisplay.test.ts:10` | None |
| AC-03 | Demo question chips are present | PASS | `chat-ui/src/lib/demoQuestions.ts:1`; `chat-ui/src/lib/demoQuestions.test.ts:12` | None |
| AC-04 | SSE token streaming and `final` event contract | PASS | `assistant-app/src/main/java/dev/localassistant/assistant/web/ChatController.java:35`; `SseAssistantResponseSink.java:23`; `chatController.test.ts:42` | None |
| AC-05 | UI and API surface errors/source-unavailable states | PASS | `chatController.test.ts:91`; latest weather capture records `weather_observation` as `UNAVAILABLE` without a fabricated temperature in `docs/demo/final-answers.md` | None |
| AC-06 | Ollama synthesis available behind `LlmPort` | PASS | `OllamaLlmAdapterContractTest.java:80`; `./mvnw test` passed | None |
| AC-07 | Ollama unavailable path does not fabricate synthesis | PASS | `OllamaLlmAdapterContractTest.java:145`; `AnswerQuestionUseCaseTest.java:276` | None |
| AC-08 | RAG ingestion stores CDQ Fraud Guard content in pgvector | PASS | `ASSISTANT_INGEST_RAG=true ./mvnw -pl assistant-app spring-boot:run` ended `RAG ingestion complete`, `chunk-count: 9`, `outcome: UNCHANGED`; `RagIngestionUseCaseIntegrationTest.java:79` | None |
| AC-09 | RAG ingestion CLI also supports `--ingest-rag` | PASS | `./mvnw -pl assistant-app spring-boot:run -Dspring-boot.run.arguments=--ingest-rag` ended `RAG ingestion complete`; `RagIngestionMode.java:11` | None |
| AC-10 | RAG retrieval returns relevant CDQ snippets | PASS | `RagRetrievalIntegrationTest.java:85`; `docs/demo/request-traces/05-cdq-product.txt` records `ragRetrievalCount=5`; `docs/demo/final-answers.md` records the captured CDQ answer | None |
| AC-11 | No relevant RAG knowledge is reported honestly | PASS | `RagRetrievalIntegrationTest.java:122`; `AnswerQuestionUseCaseTest.java:263` | None |
| AC-12 | Countries MCP uses REST Countries v5 and API key configuration | PASS | `docs/spec/11-countries-mcp-tool-contract.md` updated; `RestCountriesHttpAdapter.java:27`; `.mcp.json` uses `https://api.restcountries.com/countries/v5` and `REST_COUNTRIES_API_KEY` | None |
| AC-13 | Country output includes population | PASS | `CountryToolResult.java:14`; live Germany capture: `population: 83497147` | None |
| AC-14 | Country unavailable path blocks dependent weather answer | PASS | `AnswerQuestionUseCaseTest.java:299`; no model fallback is used for failed country facts | None |
| AC-15 | Weather MCP success mapping is covered | PASS | `WeatherMcpClientAdapterIntegrationTest.java:42`; `AnswerQuestionUseCaseTest.java:173` | None |
| AC-16 | Weather MCP unavailable path is honest | PASS | latest capture answers: `Weather MCP is unavailable: MCP tool call failed for get-weather`; `WeatherMcpClientAdapterIntegrationTest.java:77` | None |
| AC-17 | Live weather demo returns current temperature | BLOCKED | `./scripts/capture-demo-answers.sh` exited 0 but captured weather source as `UNAVAILABLE` for Munich and Berlin | Fix weather MCP timeout/configuration, rerun capture, update `docs/demo/final-answers.md` only from real output |
| AC-18 | Deterministic source routing for six demo questions | PASS | `SourceRoutingPolicy.java:26`; E2E route tests passed | None |
| AC-19 | Model does not run unbounded autonomous tool loop | PASS | `SourceRoutingPolicy.java:9`; ADR 0010 remains `Proposed` | None |
| AC-20 | Demo answers are captured only from runtime | PASS | `docs/demo/final-answers.md` updated from `./scripts/capture-demo-answers.sh` output; no invented weather value added | None |
| AC-21 | README reviewer path is compact and runnable | PASS | README updated for current prerequisites, commands, limitations, and evidence pointers | None |
| AC-22 | AI usage disclosure exists | PASS | `docs/ai/summary.md` describes AI use and human verification responsibility | None |
| AC-23 | Hexagonal boundaries hold for domain/application code | PASS | grep for Spring/MCP/JDBC/HTTP client imports in domain/application packages found no forbidden imports; only string label `OLLAMA_SOURCE_LABEL` | None |
| AC-24 | No skipped tests in source/test trees | PASS | `rg -n "@Disabled|\\.skip\\(|\\.only\\(|xit\\(" ...` returned no matches | None |
| AC-25 | Chat UI build passes | PASS | `cd chat-ui && npm test && npm run build` exited 0; 42 tests passed; Astro built 1 page | None |
| AC-26 | Dependency audit has no production high/critical findings | PASS | `npm audit --omit=dev` reported 2 low-severity esbuild findings through Astro | Track as hardening item; avoid forced downgrade without compatibility check |

### Phase Exit Gates

| ID | Requirement | Status | Evidence | Gap / Fix If Not PASS |
|---|---|---:|---|---|
| P0 | Documentation-first phase complete before production code | PASS | specs, ADRs, README, demo docs, and AI usage docs exist and were read before edits | None |
| P1 | Maven multi-module project builds/tests | PASS | `./mvnw test` and `./mvnw verify` both exited 0 | None |
| P2 | Countries MCP package and tests work | PASS | `./mvnw -pl countries-mcp-server -am package -DskipTests` exited 0; 33 country-server tests passed in full runs | None |
| P3 | Weather and external tool contracts are tested | PASS | weather adapter integration tests pass in `./mvnw test` | Live weather capture still blocked; see AC-17 |
| P4 | RAG ingestion/retrieval over pgvector works | PASS | both ingestion paths succeeded; RAG integration tests passed | None |
| P5 | Assistant orchestration and honesty rules work | PASS | `AnswerQuestionUseCaseTest` covers happy and unavailable paths; E2E passed | None |
| P6 | Assistant API and Chat Interface are implemented | PASS | API contract and UI tests/build pass | Manual Chat Interface browser smoke was not run in this audit |
| P7 | Demo answers and traces captured from runtime | BLOCKED | six answers were captured, but the two weather routes captured source-unavailable instead of current observations | Restore weather MCP success and recapture |
| P8 | Submission docs are current and recruiter-facing | PASS | README, clean checkout verification, final answers, request traces, MCP docs, and backlog/report updated | None |

### Demo Questions

| ID | Requirement | Status | Evidence | Gap / Fix If Not PASS |
|---|---|---:|---|---|
| DQ-01 | "What is the capital of Germany?" uses countries source | PASS | capture trace `74502d08-5764-450e-a4dd-baddbc1cfd0a`; `countries_facts` `USED`; answer `The capital of Germany is Berlin.` | None |
| DQ-02 | "What is the weather in Munich?" uses weather source and current observation | BLOCKED | capture trace `b39586dd-1c17-4274-a7da-d8096b2a9d6e`; `weather_observation` `UNAVAILABLE` | Fix weather MCP timeout/config, rerun capture |
| DQ-03 | "What is the capital of Germany and what is the weather there?" uses countries then weather | BLOCKED | capture trace `6a6cdbf3-d248-4c92-969d-7b8c92e0ea73`; countries `USED`, weather `UNAVAILABLE`; answer does not invent weather | Fix weather MCP timeout/config, rerun capture |
| DQ-04 | "Tell me about Berlin" uses countries facts and synthesis, not weather/RAG | PASS | capture trace `8a37be2d-d30a-4f61-b4ff-e8b84bbb0574`; sources `countries_facts` and `model_synthesis` only | P2 polish: remove duplicate wording and spacing issue |
| DQ-05 | "What is CDQ Fraud Guard?" uses RAG and synthesis | PASS | capture trace `1719bc8f-b8b3-4e95-9c74-d4b386c8a641`; 5 RAG snippets; model synthesis `USED` | None |
| DQ-06 | Unsupported fictional country reports source unavailable | PASS | capture trace `44147d25-1166-4535-b943-1c91619d7883`; no sources; answer states no configured source can answer | None |

## Verification Log

| Command | Outcome | Actual Output Summary |
|---|---:|---|
| `./mvnw test` | PASS | `BUILD SUCCESS`; assistant-app tests: 251 run, 0 failures, 0 errors, 0 skipped; countries-mcp-server tests: 33 run, 0 failures, 0 errors, 0 skipped; e2e parser tests: 3 run, 0 failures |
| `./mvnw verify` | PASS | `BUILD SUCCESS`; same test totals; assistant-app and countries-mcp-server jars packaged |
| `cd chat-ui && npm test && npm run build` | PASS | Vitest: 9 files passed, 42 tests passed; Astro: `1 page(s) built in 413ms` |
| `./mvnw -pl countries-mcp-server -am package -DskipTests` | PASS | `BUILD SUCCESS`; Spring Boot repackaged `countries-mcp-server-0.1.0-SNAPSHOT.jar` |
| `./scripts/start-assistant.sh` | BLOCKED | exited 1: `ERROR: Port 8080 is already in use. Stop the other process or set ASSISTANT_BACKEND_PORT.` |
| `ASSISTANT_INGEST_RAG=true ./mvnw -pl assistant-app spring-boot:run` | PASS | active profile `ingest-rag`; `RAG ingestion complete`; source URL CDQ Fraud Guard; `chunk-count: 9`; `outcome: UNCHANGED` |
| `./mvnw -pl assistant-app spring-boot:run -Dspring-boot.run.arguments=--ingest-rag` | PASS | same ingestion report; proves README's argument path works after the refactor |
| `./mvnw verify -P e2e` | PASS | `RequiredDemoQuestionsIT`: 5 tests run, 0 failures, 0 errors, 0 skipped; total 02:47 |
| `./scripts/capture-demo-answers.sh` | PARTIAL | exited 0 and captured all six answers; weather routes captured source-unavailable due `MCP tool call failed for get-weather` |
| `cd chat-ui && npm audit --omit=dev` | LOW FINDINGS | 2 low-severity esbuild findings via Astro; forced audit fix would change Astro version and needs deliberate upgrade/downgrade review |

The detailed command table was also refreshed in `docs/demo/clean-checkout-verification.md`.

## Documentation Changes Made

| File | Why |
|---|---|
| `README.md` | Aligned demo and limitation language with latest verified evidence, especially weather source-unavailable behavior |
| `.env.example` | Made `REST_COUNTRIES_API_KEY` an active required local value instead of a future migration note |
| `.mcp.json` | Updated REST Countries v5 URL/API-key configuration and weather MCP command |
| `docs/spec/11-countries-mcp-tool-contract.md` | Removed stale v3.1/no-auth contract language; documented v5 fields including population |
| `docs/demo/final-answers.md` | Replaced prior demo answers with current runtime capture and weather blocker |
| `docs/demo/request-traces/*.txt` | Refreshed trace IDs and route evidence from latest capture |
| `docs/demo/clean-checkout-verification.md` | Recorded current command results, local versions, and blockers |
| `docs/plans/recruiter-improvement-backlog.md` | Added scoped interview-ready improvement proposals |
| `docs/reviews/recruitment-readiness-audit.md` | Added this audit report |

ADRs 0001-0010 were checked for status drift. ADR 0010 remains `Proposed`, and no documentation now presents bounded agentic orchestration as shipped behavior.

## Remaining Gaps

### P0

- Live weather demo is blocked. The latest capture has no current temperature for Munich or Berlin because the weather MCP call failed. This must be fixed or explicitly accepted before submission, then `./scripts/capture-demo-answers.sh` should be rerun.

### P1

- `./scripts/start-assistant.sh` was not verified from a free-port state because another process already held port 8080. The script correctly failed fast, but a clean start still needs a rerun after freeing the port or setting `ASSISTANT_BACKEND_PORT`.
- No browser-level Chat Interface smoke test was run. Unit tests and build pass, but the audit did not prove the full browser path against a live backend.

### P2

- The Berlin place answer is grounded but rough: duplicated wording and malformed `population of83,497,147` spacing.
- `npm audit --omit=dev` reports low-severity esbuild exposure through Astro. It is local-demo scope, but should be tracked and resolved deliberately.

## Suggested Interview Talking Points

- The assistant uses deterministic routing for the recruitment demo because the six source routes are fixed, observable, and easier to verify than an autonomous loop.
- Source-unavailable behavior is intentional: the app names failed sources and refuses to substitute model knowledge for weather, countries, or RAG evidence.
- The next reliability improvement is a doctor/readiness check so a reviewer sees missing models, ports, Docker, API keys, or MCP failures before asking demo questions.
- ADR 0010 is the planned evolution path: bounded orchestration behind the same ports, with strict max steps, typed outcomes, cancellation, and trace evidence.
- A browser smoke test would close the main UI confidence gap by proving demo chips, streaming, traces, and final metadata in one end-to-end flow.

## Honest Limitations For Clean Machines

- Reviewers need Java 21, Node/npm, Docker, Ollama, `qwen3:4b`, `nomic-embed-text`, `REST_COUNTRIES_API_KEY`, and weather API configuration.
- Ports 8080, 4321, and 5432 must be free or overridden.
- First Ollama calls and RAG ingestion can be slow on local hardware.
- Weather answers depend on the local weather MCP command and provider availability; current capture shows this path can time out.
- The Chat Interface has automated tests and a production build, but this audit did not run a browser smoke test against the live stack.

## Suggested Commits

- `docs: refresh recruitment evidence`
- `docs: add readiness audit and backlog`
