# Chat Interface improvements — implementation notes

Running log of decisions not spelled out in `improve-chat-interface.md`.

## Session start

- Branch: `main`
- Agentic M5 **not landed** — M2b (remove `applyUnsupportedLayout`) deferred per plan release gate.

## M1 commit hygiene

- Commit `c6bccda` accidentally included `ProductKnowledgePort` move from unstaged working tree.
- Fix commit `3731a72` intended to revert only that file but also picked up ~53 other pre-existing `assistant-app` working-tree changes that were already modified locally. **Deviation:** those backend changes are outside this plan's scope; recommend user review/split that commit.

## Pre-land blockers (M5)

- Fixed stale SSE handler race: terminal/inactive turn guard in `chatController`.
- Fixed HTTP 400 phantom turn: validation-error turns now re-render in thread.
- Fixed unknown trace `type` crash: `resolveSourceLabel` fallback.

## M2b deferred

- `applyUnsupportedLayout()` retained until `improve-agentic-tool-orchestration.md` M5 lands.
