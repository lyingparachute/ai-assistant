# Architecture Decision Records

Architecture Decision Records describe material technical decisions and the reasons behind them. They are useful in this project because reviewers should be able to see why a framework, boundary, integration pattern, or trade-off was chosen.

Create an ADR when a decision affects architecture, dependencies, runtime topology, data model, testing strategy, or public behavior.

## Suggested Naming

Use a sequential filename:

```text
0001-short-decision-title.md
```

## ADR Template

```markdown
# ADR 0000: Decision Title

## Status

Proposed | Accepted | Superseded

## Context

What problem or constraint requires a decision?

## Decision

What decision was made?

## Consequences

What improves, what becomes harder, and what trade-offs are accepted?

## Alternatives Considered

- Alternative:
  - Reason rejected:

## Verification

How will this decision be verified in code, tests, documentation, or local runtime behavior?
```
