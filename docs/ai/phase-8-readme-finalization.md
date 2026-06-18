# Phase 8: README Finalization

## Date

2026-06-18

## Task

Finalize reviewer-facing README setup instructions so a recruiter can install prerequisites, start local dependencies, run the assistant, run tests, and find demo evidence.

## AI Assistance Used

Codex updated the documentation after inspecting README requirements, demo evidence, startup scripts, environment template, and application configuration.

## Human Review

Pending reviewer confirmation. The human developer requested the README be made complete for recruiter setup.

## Files Affected

- `README.md`
- `docs/ai/phase-8-readme-finalization.md`

## Verification

```text
git diff --check -- README.md
```

Result: no whitespace errors.

```text
rg -n "Node.js|npm|lsof|python3|Ollama|localhost:11434|ASSISTANT_BACKEND_PORT|CHAT_UI_PORT|AI Usage|Architecture|Demo Questions|Limitations" README.md
```

Result: required setup and submission sections are present.

## Limitations

This pass verified documentation consistency only. It did not recapture live Demo Answers or rerun the live e2e profile.
