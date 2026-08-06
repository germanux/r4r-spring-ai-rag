# PC code review (Ring)

## Current evidence-based status

- Active task: `task-07-populate-production-rag` (`pc-runtime/progress.json`).
- Deterministic gate evidence exists with `gate_exit: 0` for attempt 1 (`worker-requests/PC.json`).
- Closure evidence is incomplete: `codex_decision` is `null` and `checkpoint_head` is `null` in the same request artifact.
- Task remains `BLOCKED` in progress state, so no acceptance can be claimed.

## First current defect (PC)

The first defect is **process/closure incompleteness**, not an implementation bug: the gate-green checkpoint has not yet received SURGICAL Codex disposition (`ACCEPT`/`REVISE`). Running another PC implementation loop now would duplicate effort and risk drift from the already-green evidence.

## Bounded next action package

- **Implementation level:** Level 3
- **Assigned role:** SURGICAL Codex (review-only)
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:**
  - Existing gate-green evidence from `run_id=20260806T190026Z`, attempt 1.
  - Mandatory review policy in `.opencode/task-plan.hierarchy.json`.
- **allowed_paths:** `[]` (read-only review pass; no product edits)
- **Exact gate:** Reuse existing exact task gate contract from `.opencode/task-plan.backend.json` for `task-07-populate-production-rag`; do **not** trigger a new PC implementation cycle unless Codex returns `REVISE`.
- **Required SURGICAL review:** Yes (mandatory for closure).

## Acceptance conditions

1. SURGICAL emits explicit `ACCEPT` or `REVISE` for the existing checkpoint evidence.
2. If `ACCEPT`: controller completes closure path (`exact-gate-green + scope-clean + surgical-accept + controller-commit`).
3. If `REVISE`: Ring issues one bounded PC correction pass on the first cited defect only.

## Avoid repeating

- Do not run another full backend gate+implementation loop while `codex_decision` for the current gate-green evidence remains `null`.

## Evidence paths

- `runtime/ring-agent/ring/20260806T190129Z/worker-requests/PC.json`
- `runtime/ring-agent/ring/20260806T190129Z/pc-runtime/progress.json`
- `runtime/ring-agent/ring/20260806T190129Z/pc-runtime/previous-ring-qwen3-directive.json`
