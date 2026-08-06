# LP code review (RUN 20260806T143139Z)

## Current evidence

- Active frontend task remains `task-fe-03c-citations` and status is still `PENDING` (`lp-runtime/progress.json`).
- Current snapshot includes a green exact gate summary (`lp-runtime/gate_summary.md`, exit 0).
- Runtime manifest has no current Codex review artifact (`lp-runtime/manifest.json`, `codex_review: null`).
- LP worktree snapshot includes frontend spec change plus non-task dirty docs/memory files (`lp-git-status.txt`, `lp-git-diff-stat.txt`).

## First current defect

Closure evidence is incomplete: FE-03C appears gate-green but lacks recorded SURGICAL decision, and dirty non-task paths risk failing scope-clean closure.

## Directed next action package

- **Implementation level:** 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03c-citations` (package `FE-03C-A` currently revise lane)
- **Dependencies:** `task-fe-03b-answer-abstention:ACCEPTED` (already satisfied)
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts` (for FE-03C-A)
- **Exact gate:** `./scripts/frontend-task-gate.sh task-fe-03c-citations`
- **Required SURGICAL review:** mandatory ACCEPT/REVISE decision before closure

### One-pass instruction

Run a **review-only pass** first: submit current FE-03C evidence/diff for SURGICAL decision. Do not add new LP edits unless SURGICAL returns `REVISE`.

## Acceptance conditions

1. SURGICAL Codex decision is recorded (`ACCEPT` or `REVISE`) for the present gate-green evidence.
2. If `ACCEPT`, scope-clean check confirms no disallowed task edits are included at closure.
3. If `REVISE`, execute exactly one bounded FE-03C correction pass inside allowed path and rerun exact gate.

## Avoid repeating

Do not keep iterating FE-03C assertions without first resolving the pending SURGICAL review decision.
