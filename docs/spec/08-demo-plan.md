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
8. Ask the required demo questions exactly as written below.
9. Save the assistant responses and verification notes under the final demo evidence location.

The final README must replace these high-level steps with exact verified commands after implementation.

## Required Questions

The required demo questions are:

- "What is the capital city of Germany?"
- "What is the temperature currently in Munich?"
- "What is the temperature of the capital of Germany currently?"
- "What do you know about Berlin?"

Additional showcase questions should demonstrate at least one of:

- CDQ Fraud Guard RAG retrieval;
- multi-step country plus weather orchestration;
- source-unavailable behavior;
- clear distinction between retrieved facts and general model synthesis.

## Expected Source Path for Each Question

| Demo question | Expected source path |
| --- | --- |
| "What is the capital city of Germany?" | Chat UI -> Assistant API -> Assistant Application Service -> Countries Port -> Countries MCP client adapter -> custom countries MCP server -> REST Countries |
| "What is the temperature currently in Munich?" | Chat UI -> Assistant API -> Assistant Application Service -> Weather Port -> weather MCP client adapter -> local weather MCP server |
| "What is the temperature of the capital of Germany currently?" | Chat UI -> Assistant API -> Assistant Application Service -> Countries Port -> countries MCP path -> Weather Port -> weather MCP path -> Response Composer |
| "What do you know about Berlin?" | Chat UI -> Assistant API -> Assistant Application Service -> Countries Port (resolves Berlin to Germany) -> LLM Port (synthesis) -> Response Composer. Weather Port and RAG Port do not fire. The answer states Berlin is the capital of Germany from the countries source and must not present unsourced specifics as verified facts or mislabel model synthesis as a tool result. |

## Where to Save Final Answers

Use a dedicated demo evidence location after implementation, for example:

```text
docs/demo/final-answers.md
docs/demo/demo-run-log.md
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

## Weather Answer Requirements

Weather answers must include timestamp and location because current weather is volatile. A numeric temperature without those details is not enough evidence for a reviewer to understand what was measured.

Weather answers should include:

- requested or resolved location, such as Munich or Berlin;
- current temperature returned by the weather source;
- a timestamp labeled as either the observed time from the weather source or, when the source provides none, the adapter retrieval time. The two must not be conflated;
- source status.

For "What is the temperature of the capital of Germany currently?", the answer should also make clear that the capital was resolved through the countries capability before weather lookup.

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
- source paths are documented for each answer;
- trace or log evidence exists for each required question;
- weather answers include location and timestamp;
- any unavailable source is documented honestly;
- no final answer is fabricated in documentation.
