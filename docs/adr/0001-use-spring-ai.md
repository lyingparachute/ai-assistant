# ADR 0001: Use Spring AI

## Status

Accepted

## Context

The assignment requires a local Java AI Assistant with Ollama integration and RAG over pgvector. The project should stay production-minded, testable, and realistic for a recruitment task.

Spring Boot is already the intended application framework in `README.md`. Spring AI provides Java abstractions for model clients, Ollama integration, embeddings, prompts, and vector-store-related workflows that fit the required capabilities.

## Decision

Use Spring AI for Java LLM integration, Ollama integration, and RAG-related abstractions where suitable. Spring AI supplies the embedding and model clients; it does not own the vector-store schema, which the project's pgvector adapter owns (ADR `0003`, ADR `0007`).

Application services will still own orchestration and domain ports. Spring AI is an adapter helper, not the assistant orchestration model. Spring AI types are allowed only in outbound adapters and configuration. They must not appear in domain or application code, which depends on `LlmPort` and the other application ports instead.

## Consequences

Benefits:

- Reduces custom infrastructure code for Ollama and embedding workflows.
- Aligns with Spring Boot conventions expected in a Java recruitment task.
- Gives a path to pgvector and RAG integration without inventing all plumbing.
- Keeps local model configuration externalized through Spring configuration.

Trade-offs:

- Spring AI versions and APIs may change, so dependency versions must be pinned and documented.
- Careless use could leak framework types into the domain.
- Treating Spring AI as the core architecture would hide routing decisions and make tests weaker.
- Some RAG behavior may still need custom code for deterministic chunk metadata and source-unavailable behavior.

## Alternatives Considered

- Raw HTTP calls to Ollama:
  - Reason rejected: more boilerplate, weaker framework integration, and higher risk of ad hoc prompt and embedding code.
- LangChain4j:
  - Reason rejected: viable, but less aligned with the Spring Boot direction already selected for this repository.
- Custom LLM abstraction only:
  - Reason rejected: still useful as a port, but not enough by itself to avoid implementing infrastructure plumbing from scratch.

## Verification

- `assistant-app` uses Spring AI only through outbound adapters or configuration.
- `LlmPort` tests run with controlled adapters and do not require Spring AI internals.
- README documents the selected Spring AI version and local configuration once implementation exists.
