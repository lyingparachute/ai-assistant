# Acceptance Criteria

## Chat Interface

- A reviewer can start the assistant locally using documented commands.
- A reviewer can submit a natural-language question through the chat interface.
- The chat interface displays the assistant response and clear error messages when a required dependency is unavailable.
- The Chat Interface keeps a **session display**: prior questions and answers remain visible in the browser for the current session. Refresh clears the display. Each API request still sends only the current question — no prior-turn payload (ADR `0006`).
- Demo question chips in the Chat Interface load from the shared `demo-questions.json` fixture; clicking a chip fills the composer without auto-submit.
- The chat path is covered by at least one automated test or documented repeatable verification.

## Ollama Integration

- The assistant sends completion requests to a locally running Ollama instance.
- The model name is configurable and defaults to the assignment model through documented configuration.
- If Ollama is unavailable, the assistant returns a clear failure instead of a fabricated answer.
- A focused test verifies successful model-call orchestration through a controlled adapter or local test setup.

## RAG Ingestion

- Plain text is extracted from the CDQ Fraud Guard product page.
- The extracted text is chunked with deterministic metadata.
- Chunks are embedded with `nomic-embed-text` (dimension `768`) and stored in pgvector as `vector(768)` (ADR `0007`).
- Re-running ingestion on a clean local database produces retrievable chunks.
- A focused test verifies ingestion with representative product text.

## RAG Retrieval

- A CDQ Fraud Guard question retrieves relevant chunks from pgvector.
- The assistant uses retrieved chunks as grounding context.
- If no relevant chunks are found, the assistant reports insufficient product knowledge.
- A focused test verifies both relevant retrieval and no-result behavior.

## Countries MCP Server

- The repository includes a custom MCP server that calls REST Countries.
- A country-capital query for Germany returns Berlin through the MCP path.
- A capital-city query for Berlin resolves to Germany through the MCP path.
- Country tool output includes the fields needed by the assistant: country name, capital, region, and population.
- REST Countries failures are surfaced as source failures.
- Contract tests pin the tool name, required fields, compact output shape, and recovery-hint error shape.
- Tests cover successful lookup, unavailable-source behavior, and invalid-country or invalid-capital behavior without uncontrolled network access.

## Weather MCP Integration

- The assistant integrates with the local `semdin/mcp-weather` server.
- A weather query for Munich requests current weather through the MCP path.
- Weather-server failures are surfaced without invented temperature values.
- Tests cover successful weather lookup through a controlled adapter and source-unavailable behavior.

## Demo Questions

- "What is the capital city of Germany?" is answered from the countries capability.
- "What is the temperature currently in Munich?" is answered from the weather capability.
- "What is the temperature of the capital of Germany currently?" combines countries and weather capabilities.
- "What do you know about Berlin?" fires the countries and LLM ports only (not weather or RAG), states that Berlin is the capital of Germany from the countries source, and does not present any unsourced specific fact as verified or mislabel model synthesis as tool output.
- Additional showcase questions are documented and answered from a running assistant.
- Final demo answers are recorded only after the assistant has been run.

## Tests

- The documented test command runs from a clean checkout after local prerequisites are available.
- Tests cover happy paths and source-unavailable paths.
- Tests do not require secrets or machine-specific local paths.
- A failing required dependency produces a meaningful test failure or skipped external verification with an explicit documented reason.
- Each implementation phase records the focused test command and actual output before the phase is marked complete.

## README

- `README.md` explains overview, architecture, tech stack, prerequisites, local setup, running the assistant, running tests, demo questions, AI usage, and limitations.
- Setup and test commands are updated when implementation exists.
- Limitations and unfulfilled tasks are stated plainly.

## AI Usage Explanation

- AI-assisted work is documented under `docs/ai/`.
- Each material AI-assisted change records the task, tool or agent used, human review, files changed, and verification evidence.
- The final submission explains how AI contributed and which parts were human-reviewed.
