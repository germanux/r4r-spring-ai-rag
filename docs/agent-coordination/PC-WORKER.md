# PC code review (Ring)

## Current evidence-based status

- Active task: `task-07-populate-production-rag` (`pc-runtime/progress.json`).
- Deterministic gate evidence is green for the current request (`gate_exit=0`) in `worker-requests/PC.json`.
- Task is still marked `BLOCKED` and `codex_decision` is `null`; no Codex review artifact is present in this run (`pc-runtime/manifest.json` has `codex_review: null`).

## First current defect (PC queue)

The queue is attempting to move forward without the required SURGICAL acceptance checkpoint. This is a process/closure defect, not a new coding defect.

## Bounded next action package

- **Implementation level:** 3 (SURGICAL review pass only)
- **Assigned role:** SURGICAL (`r4r-surgical-architect` / `r4r-surgical-fixer`), not PC
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:**
  - Existing PC gate-green checkpoint request already captured
  - Mandatory reviewer policy from `.opencode/task-plan.hierarchy.json`
- **allowed_paths:** none for this review-only pass (read-only review of existing evidence and diff)
- **Exact gate:**
  - Keep task authoritative gate unchanged from `.opencode/task-plan.backend.json`:
    - `bash -lc "rm -rf target && ./scripts/task-gate.sh all && ... SELECT count(*) FROM vector_store ... test \"$rows\" -gt 0"`
- **Required SURGICAL review:** mandatory; must return `ACCEPT` or `REVISE` before any new PC implementation pass.

## Acceptance conditions

1. SURGICAL Codex emits explicit `ACCEPT` or `REVISE` on the current task-07 checkpoint evidence.
2. If `REVISE`, Ring issues exactly one new bounded PC correction pass using the first current failure.
3. Do not run additional PC edit/gate loops while `codex_decision` remains `null` for this same gate-green request.

## Evidence paths

- `/home/german/Desarrollo/r4r-ring-agent.git/runtime/ring-agent/ring/20260806T194134Z/worker-requests/PC.json`
- `/home/german/Desarrollo/r4r-ring-agent.git/runtime/ring-agent/ring/20260806T194134Z/pc-runtime/progress.json`
- `/home/german/Desarrollo/r4r-ring-agent.git/runtime/ring-agent/ring/20260806T194134Z/pc-runtime/manifest.json`
- `/home/german/Desarrollo/r4r-ring-agent.git/runtime/ring-agent/ring/20260806T194134Z/pc-runtime/previous-ring-qwen3-directive.json`
