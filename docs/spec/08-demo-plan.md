# Demo Plan

Final demo answers must be captured from the running assistant after implementation. This document defines how to run the demo and where evidence should be saved. It does not contain final assistant answers yet.

## How to Run the Final Demo

1. Start PostgreSQL with pgvector using the documented `pgvector/pgvector:pg17` setup.
2. Start Ollama and confirm the configured model is available.
3. Start the custom countries MCP server.
4. Start or configure the local `semdin/mcp-weather` MCP server.
5. Run CDQ Fraud Guard RAG ingestion.
6. Start `assistant-app`.
7. Open the Chat Interface.
8. Ask the required demo questions exactly as written below. Optionally use the **demo question chips** in the Chat Interface to fill the composer (click does not auto-submit).
9. Save the assistant responses and verification notes under the final demo evidence location.

The final README must replace these high-level steps with exact verified commands after implementation.

## Manual API probe (SSE)

With the assistant running on port `8080`, use `curl -N` so events arrive incrementally:

```bash
# Deterministic route — expect trace + final, no token events
curl -sfN -X POST http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"question":"What is the capital city of Germany?"}'

# Synthesis route — expect trace, token(s), model_synthesis trace, final
curl -sfN -X POST http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"question":"What do you know about Berlin?"}'
```

The capture script `scripts/capture-demo-answers.sh` uses the same transport and extracts the
terminal `final` event JSON for each demo question.

## Demo Verification Command

The demo questions and source paths are verified by `RequiredDemoQuestionsIT` in the `e2e-tests`
module. It is opt-in and runs only under a Maven profile so the default build stays hermetic:

```bash
./mvnw verify -P e2e
```

Without `-P e2e` the integration test does not run. With `-P e2e` it fails (it does not skip) when
no assistant responds on the configured base URL (`assistant.e2e.base-url`, default
`http://localhost:8080`). The demo questions are defined once in
`e2e-tests/src/test/resources/demo-questions.json`; the capture script
`scripts/capture-demo-answers.sh` and the integration test both read that file.

The capture script writes raw local JSON under `docs/demo/capture/`, then the curated submission
evidence is summarized in `docs/demo/final-answers.md` with trace excerpts under
`docs/demo/request-traces/`. The capture script fails non-zero if the question file is missing,
malformed, or empty rather than capturing nothing.

## Required Questions

The demo questions are defined once in `e2e-tests/src/test/resources/demo-questions.json`. The four
required questions carry the keys `germany-capital`, `munich-weather`, `germany-capital-weather`, and
`berlin-place`. The showcase questions (`cdq-product`, `source-unavailable-invalid-country`) cover at
least one of:

- CDQ Fraud Guard RAG retrieval;
- multi-step country plus weather orchestration;
- source-unavailable behavior;
- clear distinction between retrieved facts and general model synthesis.

## Expected Source Path for Each Question

Rows are keyed to `demo-questions.json` so the question wording is not duplicated here.

| Question key | Expected source path |
| --- | --- |
| `germany-capital` | Chat Interface -> Assistant API -> Assistant Application Service -> Countries Port -> Countries MCP client adapter -> custom countries MCP server -> REST Countries |
| `munich-weather` | Chat Interface -> Assistant API -> Assistant Application Service -> Weather Port -> weather MCP client adapter -> local weather MCP server |
| `germany-capital-weather` | Chat Interface -> Assistant API -> Assistant Application Service -> Countries Port -> countries MCP path -> Weather Port -> weather MCP path -> Response Composer |
| `berlin-place` | Chat Interface -> Assistant API -> Assistant Application Service -> Countries Port (resolves Berlin to Germany) -> LLM Port (synthesis) -> Response Composer. Weather Port and RAG Port do not fire. The answer states Berlin is the capital of Germany from the countries source and must not present unsourced specifics as verified facts or mislabel model synthesis as a tool result. |

## Where to Save Final Answers

Use this dedicated demo evidence location after implementation:

```text
docs/demo/final-answers.md
docs/demo/clean-checkout-verification.md
docs/demo/request-traces/
```

`final-answers.md` should include:

- demo run timestamp;
- local configuration summary without secrets;
- each required question;
- answer copied from the running assistant;
- source path exercised;
- request trace file or log excerpt reference;
- notes for unavailable sources, if any.

`demo-run-log.md` should include:

- commands used to start local dependencies;
- local MCP configuration used for custom countries and weather servers;
- ingestion command and result;
- ingestion artifact summary, such as extracted text size and chunk count;
- assistant startup command;
- focused test commands and actual output;
- trace or log capture command used during demo;
- any skipped verification with reason.

`request-traces/` should contain one small trace or log excerpt per required question. Each excerpt should show selected route, tool calls, RAG retrieval count when applicable, and source-unavailable outcomes. It should not include secrets, local filesystem paths, or raw stack traces.

Each trace excerpt must include:

- correlation id;
- selected route or sources;
- tool calls, if any;
- RAG retrieval count, including `0` when RAG does not fire;
- source-unavailable marker, if any.

## Weather Answer Requirements

Weather answers must include timestamp and location because current weather is volatile. A numeric temperature without those details is not enough evidence for a reviewer to understand what was measured.

Weather answers should include:

- requested or resolved location, such as Munich or Berlin;
- current temperature returned by the weather source;
- a timestamp labeled as either the observed time from the weather source or, when the source provides none, the adapter retrieval time. The two must not be conflated;
- source status.

For the `germany-capital-weather` question, the answer should also make clear that the capital was resolved through the countries capability before weather lookup.

## Documenting Unavailable External Services

If a required source is unavailable during demo capture:

- name the unavailable source;
- record the command or dependency that failed;
- record the user-facing source-unavailable response from the assistant;
- do not replace the missing value with model memory or manual lookup;
- add a follow-up note if the failure blocks a required acceptance criterion.

Examples:

- If REST Countries is unavailable, the Germany capital demo must show the countries source failure instead of claiming Berlin from memory.
- If the weather MCP server is unavailable, weather demos must not include invented temperatures.
- If CDQ extraction fails, product RAG demos must say that the product knowledge source could not be prepared.

## Demo Completion Criteria

The demo is complete only when:

- all required questions have captured answers from a running assistant;
- at least one additional showcase answer has been captured from a running assistant;
- source paths are documented for each answer;
- trace or log evidence exists for each required question;
- weather answers include location and timestamp;
- at least one source-unavailable response has been captured or an explicit blocker is documented;
- any unavailable source is documented honestly;
- no final answer is fabricated in documentation.
