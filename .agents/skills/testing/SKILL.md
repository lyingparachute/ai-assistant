---
name: testing
description: Test strategy for the Local AI Assistant. Use when writing unit, contract, integration, or E2E demo verification.
---

# Testing

Authoritative detail: `docs/spec/07-test-strategy.md` and `docs/spec/10-code-quality-guidelines.md` section 9. This skill is the checklist.

## When to Use

Use whenever you add or change behavior. Tests ship with each behavior; untested code is unfinished.

## Rules

- Test at boundaries with controlled adapters. Prefer integration-style tests over tests that mock every internal call.
- Unit tests cover deterministic domain behavior and policies. They never call Ollama, REST Countries, weather services, or pgvector.
- Contract tests cover Assistant API payloads, MCP tool input/output and error shapes, REST Countries response mapping, the RAG repository read/write shape, and the local MCP configuration shape.
- Integration tests cover orchestration with controlled ports, the countries MCP server against a stubbed REST Countries server, the pgvector adapter against a real `pgvector/pgvector:pg17` container, and the chat endpoint with controlled ports.
- Cover happy paths and source-unavailable paths for every required source.
- Tests must not require secrets, machine-specific paths, or uncontrolled network access.
- No `@Disabled`, `.skip`, or `.only` without a tracked, documented reason.
- Maintain a small evaluation dataset for routing and grounding, with deterministic assertions for source routing, required fields, and source-unavailable behavior.

## Patterns to Prefer

- Controlled port implementations (test doubles you own) for `LlmPort`, `RagKnowledgePort`, `CountriesPort`, `WeatherPort`.
- Testcontainers `pgvector/pgvector:pg17` for vector schema, storage, and retrieval.
- In-process stub HTTP servers or fixtures for REST Countries responses.
- E2E checks that assert the source path and required fields, not volatile weather values.

## Patterns to Avoid

- Mocking every internal call so the test only restates the implementation.
- Asserting an exact current temperature or other volatile value.
- Inventing a temperature, capital, or product fact when a source is unavailable.
- Live network calls in automated tests.
- Claiming a feature complete because a mock returned the expected value.

## Verification Checklist

- The narrowest meaningful test for the change passes, and you observed the actual command output.
- Happy path and source-unavailable path are both covered for each affected source.
- No fabricated runtime values appear in tests or demo verification.
- E2E demo verification asserts source-path evidence; final demo answers come only from the running assistant.
- Any skipped external verification has an explicit documented reason.
