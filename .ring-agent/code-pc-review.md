# PC code review (backend)

## Current evidence

- Active task: `task-07-populate-production-rag` (`pc-runtime/progress.json`).
- Deterministic backend gate status is green (`pc-runtime/gate_summary.md`, exit `0`).
- Worker request is a **gate-green-checkpoint** handoff, but closure is incomplete: `codex_decision: null`, `checkpoint_head: null` (`worker-requests/PC.json`).
- Task remains `BLOCKED` in progress despite gate-green evidence (`pc-runtime/progress.json`).

## First current defect

The first defect is **closure-state incompleteness**, not implementation correctness: there is no recorded SURGICAL decision or checkpoint head for the gate-green attempt.

## Bounded next action package

- **Implementation level:** 3 (SURGICAL review-only pass)
- **Assigned role:** SURGICAL Codex (`r4r-surgical-architect` / `r4r-surgical-fixer` lane)
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:**
  - Exact gate already green for run `20260806T200011Z`
  - Existing PC diff and evidence bundle from attempt 1
- **allowed_paths:** none for review-only classification in this pass (no new implementation edit requested)
- **Exact gate / closure constraint:**
  - `.opencode/task-plan.hierarchy.json` closure chain: `exact-gate-green + scope-clean + surgical-accept + controller-commit`
  - Backend task gate for task-07 remains authoritative from `.opencode/task-plan.backend.json`
- **Required SURGICAL review:** mandatory before closure; produce ACCEPT/REVISE and explicit closure-state classification.

## Acceptance evidence required in next cycle

1. Explicit SURGICAL decision for task-07 (`ACCEPT` or `REVISE`).
2. If `ACCEPT`, controller-owned closure artifacts (including checkpoint/commit state) must be present in current evidence.
3. If `REVISE`, one bounded correction packet must identify first failure and scope-limited next edit.

## Avoid repeating

Do not run another unchanged PC implementation/gate pass while `codex_decision` remains null and closure metadata is unresolved.
