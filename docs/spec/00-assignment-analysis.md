# Assignment Analysis

## Functional Requirements

- Provide a chat interface for asking questions to a local AI Assistant.
- Use Ollama with the assignment model for local language-model responses.
- Answer country-related questions through a custom MCP server backed by REST Countries.
- Answer current-weather questions through a local MCP weather server.
- Answer CDQ Fraud Guard product questions using RAG over ingested product-page content.
- Combine tool and RAG results when a question requires multiple sources.
- Provide captured final answers from the running assistant for all required demo questions.

## Integration Requirements

- Run a local Ollama model, with model selection provided by configuration.
- Run a local PostgreSQL database with pgvector using `pgvector/pgvector:pg17`.
- Extract plain text from `https://www.cdq.com/products/cdq-fraud-guard`.
- Convert extracted product text into chunks and embeddings.
- Store embeddings and source metadata in pgvector.
- Implement a custom MCP server for `https://restcountries.com/`.
- Integrate the local weather MCP server from `https://mcpservers.org/servers/semdin/mcp-weather`.

## Testing Requirements

- Provide automated tests for chat request handling.
- Provide tests for RAG ingestion and retrieval behavior.
- Provide tests for REST Countries MCP server behavior.
- Provide tests for weather MCP integration behavior.
- Provide tests for source-unavailable behavior.
- Provide tests or repeatable verification for the required demo questions.

## Documentation Requirements

- Provide a README with setup, run, and test instructions.
- Explain how AI was used to fulfill the task.
- Document architecture decisions that affect implementation structure.
- Document acceptance criteria before production code is implemented.
- Document limitations and unfulfilled tasks honestly.

## Out-of-Scope Items

- Long-term memory.
- Short-term conversational memory beyond what is required for a single chat interaction.
- Cloud model hosting.
- Production deployment to remote infrastructure.
- Paid external APIs.

## Demo Deliverables

Required demo questions:

- What is the capital city of Germany?
- What is the temperature currently in Munich?
- What is the temperature of the capital of Germany currently?
- What do you know about Berlin?

Additional own showcase questions must demonstrate capabilities beyond the minimum path, such as product knowledge retrieval, combined country and weather lookup, or graceful failure handling.
