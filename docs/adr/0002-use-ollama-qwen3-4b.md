# ADR 0002: Use Ollama qwen3:4b

## Status

Accepted

## Context

The assignment requires local model execution through Ollama and specifies model `qwen3:4b`. The repository is local-first and does not use cloud LLM providers.

The model must support assistant synthesis, but it must not be treated as a verified source for country facts, current weather, or CDQ Fraud Guard product knowledge.

## Decision

Use local Ollama model `qwen3:4b` as the configured default model.

The model name must be configurable, but the documented default for the assignment is `qwen3:4b`.

## Consequences

Benefits:

- Satisfies the assignment requirement.
- Keeps model execution local and reproducible.
- Avoids cloud model credentials and paid APIs.

Trade-offs:

- A small local model may produce lower-quality synthesis than larger cloud models.
- It may misunderstand routing intent if routing is delegated to prompts alone.
- It may hallucinate if asked to answer without grounded source results.
- Local performance depends on reviewer hardware.

The application must compensate by making source routing explicit in application code and by composing prompts from verified source results.

## Alternatives Considered

- Larger Ollama model:
  - Reason rejected: the assignment requires `qwen3:4b`, and larger models increase local hardware requirements.
- Cloud-hosted model:
  - Reason rejected: cloud model hosting is out of scope and conflicts with local-first requirements.
- No LLM:
  - Reason rejected: the assignment requires a local AI Assistant with model-backed synthesis.

## Verification

- README documents how to install Ollama and pull `qwen3:4b`.
- Configuration defaults to `qwen3:4b` without hardcoding it inside production logic.
- Tests verify that unavailable Ollama produces a clear failure when model synthesis is required.
- Demo answers are captured from the running assistant, not pre-written from model assumptions.
