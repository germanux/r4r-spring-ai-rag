# PC code review (evidence cycle 20260806T193132Z)

## Current verdict
- **Queue status:** REVIEW required before more PC implementation.
- **Active task:** `task-07-populate-production-rag`.
- **First current defect:** not a code defect yet; **closure defect** is missing mandatory SURGICAL decision (`codex_decision: null`) after a gate-green request.

## Evidence reviewed
- `runtime/ring-agent/ring/20260806T193132Z/worker-requests/PC.json` (gate exit `0`, decision still null)
- `runtime/ring-agent/ring/20260806T193132Z/pc-runtime/progress.json` (task-07 marked BLOCKED, last gate green recorded)
- `runtime/ring-agent/ring/20260806T193132Z/pc-git-status.txt` (product diffs exist and are awaiting review path)
- `runtime/ring-agent/ring/20260806T193132Z/pc-runtime/previous-ring-qwen3-directive.json` (already instructed review-only next step)

## Bounded work package
- **Implementation level:** **Level 3**
- **Assigned role:** **SURGICAL Codex (review-only pass)**
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:**
  - Existing task-07 gate-green checkpoint evidence (already produced)
  - Review policy in `.opencode/task-plan.hierarchy.json`
- **allowed_paths:** `[]` (read-only review; no product edits)
- **Exact gate / acceptance constraints:**
  1. Keep task-07 exact gate contract from `.opencode/task-plan.backend.json` authoritative.
  2. Return explicit SURGICAL outcome: `ACCEPT` or `REVISE`.
  3. Closure still requires `exact-gate-green + scope-clean + surgical-accept + controller-commit`.

## Next action (single pass)
Run one SURGICAL review-only pass on the existing task-07 checkpoint evidence and publish `ACCEPT` or `REVISE`. Do **not** run another PC edit/gate loop until that decision exists.

## Avoid repeating
- Re-running PC implementation while `codex_decision` is still null.
- Producing additional backend diffs before the pending review closes the current checkpoint.

## Required SURGICAL review for closure
Mandatory. No PC task closure claim is valid without SURGICAL Codex acceptance evidence.
