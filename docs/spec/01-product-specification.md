# Product Specification

## Product Description

The product is a local AI Assistant for a recruitment-task reviewer. The reviewer interacts with a chat interface and asks questions that may require local model reasoning, country facts, weather data, or product knowledge from the CDQ Fraud Guard page.

The assistant should make source use visible through accurate answers. It should route questions to the right capability, combine results when needed, and avoid inventing facts when a required source is unavailable.

## Primary Users

- Recruitment reviewer evaluating architecture, implementation quality, local setup, and test coverage.
- Developer running and verifying the assistant locally.

## Main User Stories

- As a reviewer, I can run the assistant locally and ask the required demo questions.
- As a reviewer, I can see that country facts come from REST Countries through the custom MCP server.
- As a reviewer, I can see that current weather comes from the configured local MCP weather server.
- As a reviewer, I can ask about CDQ Fraud Guard and receive answers grounded in ingested product-page content.
- As a developer, I can run tests that verify the important integration paths without relying on hidden manual steps.
- As a developer, I can inspect documentation that explains the architecture and limitations.

## Expected Demo Behavior

### What is the capital city of Germany?

The assistant should use the countries capability and answer with the capital city from REST Countries data. It should not rely on model memory alone.

### What is the temperature currently in Munich?

The assistant should use the weather MCP capability for Munich and answer with the current temperature returned by the weather source. The exact numeric answer must be captured only from a real run.

### What is the temperature of the capital of Germany currently?

The assistant should first identify Germany's capital through the countries capability, then request weather for that city through the weather MCP capability, then combine both results into one answer.

### What do you know about Berlin?

The assistant resolves Berlin through the countries capability (which accepts a capital-city name) and states that Berlin is the capital of Germany from the countries source. It then uses the model for concise connective synthesis around those verified facts. The weather and RAG capabilities do not fire for this question. The assistant must not present any specific claim that no source returned as a verified fact, and must not label model synthesis as a tool result.

### Additional Showcase Questions

Additional questions should demonstrate at least one of:

- RAG retrieval from CDQ Fraud Guard content;
- multi-step tool orchestration;
- graceful behavior when a source is unavailable;
- clear distinction between retrieved facts and general model synthesis.

## Source-Unavailable Behavior

If a required source is unavailable:

- The assistant must state which source failed.
- The assistant must avoid fabricating the missing result.
- The assistant may provide partial answers only when they are clearly grounded in available sources.
- The assistant should include enough detail for the developer or reviewer to diagnose the missing dependency.

Examples:

- If REST Countries is unavailable, the assistant must not claim a capital city from model memory as a verified tool result.
- If the weather MCP server is unavailable, the assistant must not invent current temperature.
- If RAG retrieval finds no relevant CDQ Fraud Guard content, the assistant must say that the product knowledge source did not contain enough information.
