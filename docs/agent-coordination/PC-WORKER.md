# PC code review — run 20260806T192632Z

## Current evidence reviewed

- Active backend task is `task-07-populate-production-rag` (`pc-runtime/progress.json`).
- Deterministic gate is green (`pc-runtime/gate_summary.md`, exit `0`).
- A checkpoint request is recorded, but `codex_decision` remains `null` (`worker-requests/PC.json`).

## First current defect

The first blocking defect is **workflow-state, not code-state**: task closure is blocked because mandatory SURGICAL review has not returned `ACCEPT` or `REVISE` for the gate-green checkpoint.

## Bounded next action package

- **Implementation level:** 3
- **Assigned role:** SURGICAL Codex (`r4r-surgical-architect` / `r4r-surgical-fixer`), review-only pass
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:**
  - Gate-green request exists for attempt 1 (`worker-requests/PC.json`).
  - Closure policy in `.opencode/task-plan.hierarchy.json` requires SURGICAL acceptance.
- **allowed_paths:** `[]` (read-only review pass; no product edits)
- **Exact gate:** Keep backend task-07 gate satisfaction from `.opencode/task-plan.backend.json`.
- **Required SURGICAL review:** Yes (this package is the mandatory review decision itself).

## Acceptance conditions and required evidence

1. A review result is recorded for the pending request with explicit `codex_decision` (`ACCEPT` or `REVISE`).
2. If `REVISE`, issue one bounded PC correction pass under task-07 allowed paths only.
3. If `ACCEPT`, permit controller-owned closeout (`exact-gate-green + scope-clean + surgical-accept + controller-commit`).

## Avoid repeating

Do **not** run another PC implementation/gate loop while this same gate-green request remains unresolved (`codex_decision=null`).
