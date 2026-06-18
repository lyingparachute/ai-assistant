# AI Usage Summary

## Purpose

This project used AI assistance as a development aid, not as a substitute for verification.
AI helped draft plans, implement scoped changes, review code, debug blockers, and update
documentation. Human review kept the assignment scope, architecture boundaries, source-routing
honesty rules, and verification requirements in control.

## How AI Helped

- Requirements and architecture: AI helped turn the assignment into product requirements,
  acceptance criteria, Architecture Decision Records, and a hexagonal architecture outline.
- Implementation: AI assisted with Spring Boot application code, MCP integrations, pgvector RAG
  ingestion and retrieval, SSE streaming, and the Astro Chat Interface.
- Review and refactoring: AI helped identify boundary leaks, unsafe string handling, UI issues,
  configuration cleanup, and source-unavailable paths.
- Debugging: AI helped diagnose REST Countries v5 migration issues, demo-capture blockers,
  build/runtime problems, and verification gaps.
- Documentation: AI helped keep README, demo evidence, ADRs, and verification notes aligned with
  the implemented behavior.

## Human Review

Human direction constrained the work to the recruitment task, selected trade-offs, rejected
unverified claims, and required live evidence for demo answers. Material changes were checked
against the source, tests, and local runtime behavior before being treated as complete.

## Verification Evidence

- Automated backend and integration verification is summarized in
  [`docs/demo/clean-checkout-verification.md`](../demo/clean-checkout-verification.md).
- Final demo answers were captured from the running assistant, not hand-written:
  [`docs/demo/final-answers.md`](../demo/final-answers.md).
- Source-Usage Trace excerpts are kept under
  [`docs/demo/request-traces/`](../demo/request-traces/).

## Limitations

Detailed per-phase AI working notes were removed from the recruiter-facing documentation to keep
the submission readable. The retained summary captures the material usage pattern; runtime
behavior remains proven by tests and captured demo evidence.
