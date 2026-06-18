# Clean-Checkout Verification

Date: 2026-06-18T08:04:13Z
Git SHA: `df10d99`
Machine context: macOS local workspace, Docker Desktop running, Ollama reachable, pgvector container `assistant-pgvector` up, existing assistant stack already listening on ports `8080` and `4321`.

This artifact records the Phase 8 command table. Output is summarized from fresh local runs; blockers are recorded as blockers, not replaced with assumed success.

| # | Command | Result | Evidence |
| --- | --- | --- | --- |
| 1 | `./mvnw test` | Pass | `BUILD SUCCESS`; assistant-app: Tests run 242, Failures 0, Errors 0, Skipped 0; countries-mcp-server: Tests run 33, Failures 0, Errors 0, Skipped 0; e2e-tests: Tests run 3, Failures 0, Errors 0, Skipped 0. |
| 2 | `./mvnw verify` | Pass | `BUILD SUCCESS`; same module test totals plus jar packaging; total time 31.903 s. |
| 3 | `./mvnw -pl countries-mcp-server -am package -DskipTests` | Pass | `BUILD SUCCESS`; Spring Boot repackaged `countries-mcp-server/target/countries-mcp-server-0.1.0-SNAPSHOT.jar`. |
| 4 | `cd chat-ui && npm install && npm run build` | Pass with audit warning | `npm install` completed; `astro build` completed with `1 page(s) built`. `npm install` also reported 3 vulnerabilities (2 low, 1 critical), not fixed in Phase 8 because this phase is documentation/verification only. |
| 5 | `./scripts/start-assistant.sh` | Blocked in this run | Existing live stack occupied port `8080`: `ERROR: Port 8080 is already in use. Stop the other process or set ASSISTANT_BACKEND_PORT.` This does not block demo verification because the existing stack was used for rows 7-8. |
| 6 | `./mvnw -pl assistant-app spring-boot:run -- --ingest-rag` | Fails as documented command | Maven error: `Unknown lifecycle phase "--ingest-rag"`. A follow-up check with `ASSISTANT_INGEST_RAG=true ./mvnw -pl assistant-app spring-boot:run` reached the `ingest-rag` profile but failed application startup because `ChatController` required missing `AnswerQuestionUseCase`. This blocks clean-checkout ingestion verification and is now called out in README limitations. |
| 7 | `./mvnw verify -P e2e` | Pass | `BUILD SUCCESS`; `RequiredDemoQuestionsIT` Tests run 5, Failures 0, Errors 0, Skipped 0; total time 01:06 min. |
| 8 | `./scripts/capture-demo-answers.sh` | Pass | Exit 0; captured six SSE terminal `final` responses from `http://localhost:8080` at `2026-06-18T08:02:27Z`; raw numbered JSON files updated under `docs/demo/capture/`. |

## Blocker Impact

The RAG ingestion command blocker affects a reviewer starting from an empty pgvector database. It does not invalidate the captured demo evidence in this workspace: the final capture used an existing pgvector database with 9 CDQ Fraud Guard chunks, and the CDQ showcase returned `rag_knowledge: USED` with 5 snippets.

The launcher row was blocked only because a stack was already running on the canonical ports. The same running stack answered E2E and capture requests successfully.
