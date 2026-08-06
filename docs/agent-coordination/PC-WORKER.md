# PC code review — run 20260806T192132Z

## Current evidence

- Active backend task is `task-07-populate-production-rag` (`pc-runtime/progress.json`).
- Deterministic gate is green (`pc-runtime/gate_summary.md`, exit `0`).
- A checkpoint request exists, but `codex_decision` is still `null` (`worker-requests/PC.json`).

## First current defect

The first blocking defect is **workflow-state, not code-state**: closure is blocked because mandatory SURGICAL review has not yet returned `ACCEPT` or `REVISE` for the gate-green checkpoint.

## Bounded next action package

- **Implementation level:** 3 (SURGICAL review control)
- **Assigned role:** SURGICAL Codex (`r4r-surgical-architect`/`r4r-surgical-fixer`), review-only pass
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:**
  - Gate-green request recorded for attempt 1 (`worker-requests/PC.json`)
  - Closure policy in `.opencode/task-plan.hierarchy.json`
- **allowed_paths:** `[]` (read-only review pass; no product edits)
- **Exact gate / constraint:**
  - Preserve backend gate satisfaction for task-07 from `.opencode/task-plan.backend.json`
  - Enforce closure rule: `exact-gate-green + scope-clean + surgical-accept + controller-commit`
- **Required SURGICAL review:** mandatory before any ACCEPT/close decision

## Acceptance evidence required

1. PC request is answered with explicit `codex_decision` (`ACCEPT` or `REVISE`).
2. If `REVISE`, one bounded PC correction pass is issued with unchanged task scope.
3. If `ACCEPT`, controller-owned closeout proceeds without additional PC implementation churn.

## Avoid repeating

Do **not** run another PC implementation/gate cycle while the same request is pending review (`codex_decision=null`).
