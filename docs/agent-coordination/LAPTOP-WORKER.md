# LP code review (evidence-based)

## Current diagnosis

- Active task: `task-fe-03d-dom-state-tests` (`lp-runtime/progress.json`: `PENDING`).
- Deterministic gate is failing (`lp-runtime/gate_summary.md`: classification `gate-failure`, exit `2`).
- LP has one modified file in scope (`lp-git-status.txt`):
  - `frontend/src/app/features/rag/rag-page.component.spec.ts`
- Current Codex packet is `REVISE` with explicit one-file corrective instructions (`lp-runtime/codex-qwen3-extra-instructions.md`).
- Prior run also records watchdog timeout, so no acceptance can be inferred (`lp-runtime/memory.md`).

## First current defect

The FE-03D test-spec correction is incomplete/unproven: gate remains red and the required one-file DOM-test structure fix has not been demonstrated by a new green run.

## Bounded next action package

- **Implementation level:** Level 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** `task-fe-03c-citations:ACCEPTED`
- **allowed_paths (canonical):** `frontend/**`, `docs/frontend/**` (effective edit target remains one file per current packet)
- **Exact gate:**
  1. `git diff --check`
  2. `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`

## Required correction focus (single pass)

Apply only the prescribed test repair in `rag-page.component.spec.ts`:
- restore valid suite structure,
- keep only the controlled-pending loading test,
- add independent success-reset and transport-error-reset DOM tests,
- preserve existing valid answer/abstention/citation/escaping coverage,
- avoid all explicitly rejected patterns in the Codex packet.

## Acceptance evidence required

1. FE-03D exact gate exits `0`.
2. Diff is whitespace-clean (`git diff --check`) and scope-clean.
3. Run evidence and understanding report map selectors/assertions to FE-03D requirements consistently.

## Avoid repeating

Do not reintroduce malformed braces/indentation, internal-state mutation, `innerHTML` mutation, guessed selectors, or synthetic response shapes rejected by the active packet.
