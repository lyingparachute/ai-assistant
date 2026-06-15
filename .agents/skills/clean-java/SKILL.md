---
name: clean-java
description: Idiomatic, clean Java for the Local AI Assistant. Use when writing or reviewing any Java code in this repository.
---

# Clean Java

Authoritative detail: `docs/spec/10-code-quality-guidelines.md` section 1 and 4. This skill is the checklist.

## When to Use

Use whenever you write or review Java in `assistant-app`, `countries-mcp-server`, `e2e-tests`, or `shared-kernel` (created only when a concrete cross-module type exists).

## Rules

- Construct objects completely from all required inputs in one place. A constructed object is always valid.
- Validate invariants in the constructor or a static factory. Reject empty question text, missing weather location, or missing timestamp at construction.
- No `null` returns. Use `Optional<T>`, empty collections, or an explicit no-result value. `Optional` on return types only, never on fields or parameters.
- No silent failures. Surface errors as typed outcomes or a clear boundary exception; never swallow an exception or return empty on error.
- Keep classes and methods small and single-purpose. Prefer package-private helpers over a wide public surface.
- No magic literals. Name every meaningful number or string as a constant in the owning module.
- Comment only hidden invariants, framework or protocol constraints, and genuinely complex flow. No narration, no removal markers, no obvious docstrings.
- Use the language from `CONTEXT.md` for type and method names.

## Patterns to Prefer

- `record` value objects with validation in the compact constructor.
- Immutable fields (`final`), immutable collections returned to callers.
- Static factory methods with intention-revealing names (`UserQuestion.of(...)`).
- Small typed wrappers over primitives (country name, location, temperature, source id).
- Typed result objects (`ToolExecutionResult`, `NoRelevantKnowledge`) instead of `null` or booleans.

## Patterns to Avoid

- Empty constructor plus setters to populate state piecemeal (except where a framework requires it).
- Returning `null` to mean "missing" or "error".
- Catching an exception and returning an empty or default value with no surfaced outcome.
- Primitive obsession: passing raw `String`/`double` where a value object carries the meaning.
- Wide god classes that orchestrate, validate, and call infrastructure together.
- Comments that restate the code.

## Verification Checklist

- The narrowest meaningful unit test for the new behavior passes, and you observed the actual output.
- No `null` returns, no swallowed exceptions, no objects that can exist in an invalid state.
- No magic literals, no dead code, no orphan `TODO`/`FIXME`/`HACK`.
- Names match `CONTEXT.md`.
