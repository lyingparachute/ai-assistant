# Risk Register

This register tracks risks that can affect assignment delivery, local reproducibility, correctness, and reviewer confidence.

## Local Model Quality

Risk: `qwen3:4b` is a small local model and may produce weak synthesis, miss tool-use intent, or overstate facts.

Mitigation: Keep routing and source selection in application code. Give the model grounded context only after required sources have been retrieved. Add orchestration tests for required demo-question paths. Keep answers concise.

Verification: Required demo questions route through ports before model synthesis. Source-unavailable tests prove the model is not used as a factual fallback.

## Ollama Not Installed

Risk: Reviewers or developers may not have Ollama installed or running locally.

Mitigation: Document Ollama installation, startup, model pull, configuration, and health checks in README. Pulling both the synthesis model `qwen3:4b` and the embedding model `nomic-embed-text` (ADR `0007`) must be documented, since RAG ingestion and retrieval fail without the embedding model. Surface Ollama failures as source-unavailable responses when model synthesis is required.

Verification: Startup or focused adapter verification fails clearly when Ollama is unavailable. README includes exact commands after implementation.

## Weather API Key Missing

Risk: The local weather MCP server may require configuration that is missing on the reviewer machine.

Mitigation: Document required environment variables and setup steps without committing secrets. Add weather source-unavailable behavior. Do not invent temperatures when the weather source cannot run.

Verification: Weather adapter tests cover unavailable-source behavior. Demo evidence records missing weather configuration if it blocks live weather capture.

## REST Countries API Unavailable

Risk: REST Countries may be unavailable, slow, or change response shape.

Mitigation: Keep REST Countries behind the custom countries MCP server and adapter boundary. Use timeouts and structured error mapping. Test against controlled fixtures or stub server responses.

Verification: Countries MCP tests cover Germany success, invalid country, and upstream failure.

## CDQ Page Extraction Instability

Risk: The CDQ Fraud Guard page may change markup, require JavaScript, or become temporarily unavailable.

Mitigation: Keep extraction logic isolated. Store source URL, content hash, and ingestion metadata. Use representative fixture text in tests. Document extraction failures instead of fabricating product knowledge.

Verification: RAG ingestion tests use controlled representative text. Manual ingestion run records source URL and result.

## pgvector Configuration Issues

Risk: Local PostgreSQL setup may miss the pgvector extension, use an incompatible image, or fail migrations.

Mitigation: Use `pgvector/pgvector:pg17` as the required local image. Use repeatable schema initialization or migrations. Use Testcontainers for automated pgvector tests.

Verification: Testcontainers test creates schema, inserts chunks, and retrieves by similarity.

## MCP Server Startup Issues

Risk: The custom countries MCP server or local weather MCP server may not start, may use mismatched transport settings, or may be unavailable when the assistant starts.

Mitigation: Keep MCP command, URL, and transport settings configurable. Add startup documentation and health checks where practical. Map connection failures to source-unavailable outcomes.

Verification: Adapter tests cover startup or call failure. Demo run log records server startup commands.

## MCP Tool Schema Noise

Risk: MCP tools may mirror upstream APIs too closely, expose unnecessary fields, or return generic errors that make the model choose tools poorly.

Mitigation: Design assistant-facing semantic tools with compact JSON schemas, clear descriptions, minimal outputs, and recovery hints. Keep upstream REST details inside adapters.

Verification: Contract tests check MCP schema shape, required fields, and invalid-input error messages.

## AI-Generated Code Quality Risks

Risk: AI-assisted implementation may introduce over-broad abstractions, hidden coupling, weak tests, or fabricated behavior.

Mitigation: Keep docs and ADRs as the implementation contract. Review code against hexagonal boundaries, `CONTEXT.md` language, and acceptance criteria. Require focused tests for each behavior.

Verification: AI usage report records files affected, human review, and verification evidence. Code review checks boundary violations and missing tests.

## Insufficient Tests

Risk: The project may pass happy-path manual demos while failure modes or routing behavior remain untested.

Mitigation: Implement tests with each behavior. Prefer boundary integration tests and controlled adapters. Include source-unavailable paths for every required source.

Verification: Test strategy checklist is covered before final submission. Focused test output is recorded in demo evidence or README.

## Weak Demo Evidence

Risk: Final demo documentation may show only answer text, making it hard for a reviewer to verify that tools and RAG were used.

Mitigation: Capture source paths plus small request traces or log excerpts for each required demo question. Keep traces free of secrets and local paths.

Verification: `docs/demo/final-answers.md` references trace evidence for each required question after implementation.

## Over-Engineering Risk

Risk: The design could become too ceremonial for a recruitment task, slowing delivery and obscuring behavior.

Mitigation: Use hexagonal architecture and DDD pragmatically. Keep domain concepts explicit but small. Avoid unnecessary aggregates, event buses, long-term memory, workflow engines, or extra frameworks.

Verification: Implementation plan keeps phases small and acceptance criteria concrete. Code review rejects abstractions that do not support a current requirement.
