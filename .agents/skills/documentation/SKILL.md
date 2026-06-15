---
name: documentation
description: Keep specs, ADRs, README, and AI usage docs current and honest for the Local AI Assistant.
---

# Documentation

Authoritative detail: `docs/spec/10-code-quality-guidelines.md` sections 11 and 12, `docs/spec/06-implementation-plan.md`, and `docs/adr/README.md`. This skill is the checklist.

## When to Use

Use whenever an implementation decision, interface, command, or limitation changes, and whenever recording AI-assisted work or demo evidence.

## Rules

- This repository is documentation-first. Production source code is not written ahead of current docs, ADRs, and acceptance criteria.
- Keep `CONTEXT.md`, `docs/spec/`, `docs/adr/`, `README.md`, and `docs/ai/` consistent. Code and documentation must not drift.
- When a decision changes architecture, dependencies, data model, testing strategy, or public behavior, add or update an ADR using the template in `docs/adr/README.md`.
- Use the language from `CONTEXT.md` everywhere. Respect its `Avoid` lists.
- Document limitations honestly, including unavailable dependencies and unfulfilled tasks.
- Record material AI-assisted work under `docs/ai/` with the task summary, agent role, files affected, human review, and verification evidence.
- Final demo answers are captured only from the running assistant (`docs/spec/08-demo-plan.md`). Never pre-write them.

## Patterns to Prefer

- Updating the relevant spec or ADR in the same change as the code that diverges from it.
- A new sequential ADR for a material decision, with status, context, decision, consequences, alternatives, and verification.
- README setup, run, and test commands updated only after they are verified from a clean checkout.
- Demo evidence that shows the source path and a small trace excerpt, free of secrets and local paths.

## Patterns to Avoid

- Documenting behavior that is not implemented as if it were verified.
- Writing final demo answers, temperatures, or other volatile values before a real run captures them.
- Leaving docs contradicting code or ADRs.
- Using avoided terms (memory, context, bot, plugin) where `CONTEXT.md` requires precise language.
- Committing secrets, local paths, or machine-specific details into docs.

## Verification Checklist

- Changed behavior is reflected in the matching spec or ADR; no drift remains.
- New material decisions have an ADR following the template.
- `CONTEXT.md` language is used; no avoided terms.
- README commands described as working were actually verified.
- AI usage entries record task, files, human review, and verification evidence.
- No fabricated demo answers or runtime values were added to documentation.
