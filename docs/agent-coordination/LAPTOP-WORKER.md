# LP code review (Ring cycle 20260807T015030Z)

## Evidence reviewed
- `runtime/ring-agent/ring/20260807T015030Z/lp-runtime/progress.json`
- `runtime/ring-agent/ring/20260807T015030Z/lp-runtime/gate_summary.md`
- `runtime/ring-agent/ring/20260807T015030Z/lp-runtime/codex_plan.json`
- `runtime/ring-agent/ring/20260807T015030Z/lp-runtime/codex-qwen3-extra-instructions.md`
- `runtime/ring-agent/ring/20260807T015030Z/lp-git-status.txt`
- `runtime/ring-agent/ring/20260807T015030Z/lp-git-diff-stat.txt`

## Current diagnosis
- Active frontend task is `task-fe-03d-dom-state-tests`.
- The deterministic gate summary reports failure (`exit=2`).
- Codex decision is `REVISE` with prescriptive one-file corrections targeting `frontend/src/app/features/rag/rag-page.component.spec.ts`.
- LP has an in-progress one-file diff, but no evidence in this RUN_DIR shows a successful rerun after applying all mandatory corrections.

## First current defect (correction before new implementation)
The defect is incomplete correction of a known failing test-file revision packet. No scope expansion is needed.

## Bounded work package
- **Implementation level:** Level 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** `task-fe-03c-citations:ACCEPTED` (already satisfied by progress evidence)
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts` (narrowed to the codex packet focus path)
- **Next action (single pass):**
  1. Restore valid suite structure and remove rejected patterns listed in the Codex packet.
  2. Keep only the prescribed three DOM tests (controlled-pending loading duplicate-submit guard, success-reset, transport-error-reset).
  3. Run `git diff --check`.
  4. Run `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests` once.
  5. Return consistent diagnostics plus requirement-to-selector assertion mapping in local understanding.

## Exact acceptance gate
- `git diff --check`
- `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
- Closure policy: `exact-gate-green + scope-clean + controller-commit`

## Avoid repeating
- Do **not** reintroduce malformed braces/indentation, trailing whitespace, guessed selectors, internal-state mutations, `innerHTML` mutation, synthetic response fields, or unnecessary `of`/`tick` usage rejected by the active packet.

## Ring repository edits
- None. Ring made no product/test/config edits.
