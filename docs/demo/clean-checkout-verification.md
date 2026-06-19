# Clean-Checkout Verification

Date: 2026-06-18T15:44:33Z
Git SHA: `0fc96c22b6d4ddcc30c04b95d84a5121acbe81ec`
Machine context: macOS local workspace; Java `21.0.9`; Node `v26.3.0`; npm `11.16.0`; Docker Desktop `29.5.2`; Docker pgvector container `assistant-pgvector` listening on `5432`; Ollama reachable on `11434`; existing Assistant API process listening on `8080`; Chat Interface port `4321` free.

This artifact records the Phase 8 command table. Output is summarized from fresh local runs; blockers are recorded as blockers, not replaced with assumed success.

| # | Command | Result | Evidence |
| --- | --- | --- | --- |
| 1 | `./mvnw test` | Pass | `BUILD SUCCESS`; assistant-app: Tests run 251, Failures 0, Errors 0, Skipped 0; countries-mcp-server: Tests run 33, Failures 0, Errors 0, Skipped 0; e2e-tests parser tests: Tests run 3, Failures 0, Errors 0, Skipped 0; total time 29.240 s. |
| 2 | `./mvnw verify` | Pass | `BUILD SUCCESS`; same module test totals plus jar packaging for assistant-app and countries-mcp-server; total time 28.259 s. |
| 3 | `cd chat-ui && npm test && npm run build` | Pass | Vitest: 9 test files passed, 42 tests passed. Astro build completed with `1 page(s) built` in 413 ms. |
| 4 | `./mvnw -pl countries-mcp-server -am package -DskipTests` | Pass | `BUILD SUCCESS`; Spring Boot repackaged `countries-mcp-server/target/countries-mcp-server-0.1.0-SNAPSHOT.jar`; total time 0.803 s. |
| 5 | `./scripts/start-assistant.sh` | Blocked in this run | Existing live backend occupied port `8080`: `ERROR: Port 8080 is already in use. Stop the other process or set ASSISTANT_BACKEND_PORT.` This does not block rows 8-9 because the existing backend answered E2E and capture requests. |
| 6 | `ASSISTANT_INGEST_RAG=true ./mvnw -pl assistant-app spring-boot:run` | Pass | `BUILD SUCCESS`; `RAG ingestion complete`; source URL `https://www.cdq.com/products/cdq-fraud-guard`; content hash `8712e9640659875fb19bc55ae3e9bf4c7877dfcc0782a26bf26756fb5b59d071`; chunk-count 9; outcome `UNCHANGED`. |
| 7 | `./mvnw -pl assistant-app spring-boot:run -Dspring-boot.run.arguments=--ingest-rag` | Pass | `BUILD SUCCESS`; same source URL, content hash, chunk-count 9, and outcome `UNCHANGED`. |
| 8 | `./mvnw verify -P e2e` | Pass | `BUILD SUCCESS`; `RequiredDemoQuestionsIT` Tests run 5, Failures 0, Errors 0, Skipped 0; e2e module time 02:20 min; total time 02:47 min. The current live weather route returned structured `UNAVAILABLE`, which the E2E contract allows. |
| 9 | `./scripts/capture-demo-answers.sh` | Pass with weather blocker | Exit 0; captured six SSE terminal `final` responses from `http://localhost:8080` at `2026-06-18T15:41:34Z`. Country facts, Berlin synthesis, CDQ RAG, and unsupported-source paths were captured. Weather-only and country-then-weather paths returned `weather_observation: UNAVAILABLE` because the weather MCP tool call timed out. |
| 10 | `cd chat-ui && npm audit --omit=dev` | Non-blocking finding | Exit 1; 2 low-severity vulnerabilities through `astro` -> `esbuild`; npm suggests `npm audit fix --force`, which would install `astro@5.17.2` and is a breaking downgrade from the submitted Astro 6 line. Tracked as an improvement item, not a release blocker. |

## Blocker Impact

The weather MCP timeout blocks fresh live temperature values for Munich and Berlin in this capture. The assistant handled the failure honestly: it returned source-unavailable responses and omitted temperatures. Controlled adapter tests still cover successful weather parsing and unavailable-source behavior.

The launcher row was blocked only because a backend was already running on the canonical port. A reviewer on a clean machine with port `8080` free should use `./scripts/start-assistant.sh`.

RAG ingestion is no longer blocked: both documented entry points completed successfully against the local pgvector database.
