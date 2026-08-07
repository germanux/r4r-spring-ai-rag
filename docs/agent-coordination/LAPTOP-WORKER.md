# LP code review (Ring)

## Current evidence read
- `lp-runtime/progress.json`: active task `task-fe-03d-dom-state-tests` (`PENDING`).
- `lp-runtime/gate_summary.md`: deterministic gate classification `gate-failure`, exit `2`.
- `lp-git-status.txt` and `lp-git-diff-stat.txt`: one edited file, `frontend/src/app/features/rag/rag-page.component.spec.ts`.
- `lp-runtime/codex_plan.json`: explicit correction packet; defect is local test-file quality/structure, not infrastructure.
- `lp-runtime/codex-qwen3-extra-instructions.md`: mandatory one-file corrective pass and prohibited patterns list.

## First current defect
FE-03D test file regression in the LP patch: formatting/suite-structure damage plus prohibited testing patterns caused gate failure. Correction must happen before any new frontend implementation.

## Bounded next package
- **Implementation level:** Level 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** `task-fe-03c-citations: ACCEPTED` (already satisfied)
- **allowed_paths (canonical):** `frontend/**`, `docs/frontend/**` (effective one-file focus from Codex packet: `frontend/src/app/features/rag/rag-page.component.spec.ts`)
- **Objective for one pass:** Repair FE-03D spec and prove loading/disabled/error/answer/reset DOM behavior through the exact gate.

### Exact action
1. Edit only `frontend/src/app/features/rag/rag-page.component.spec.ts`.
2. Restore valid pre-attempt suite structure and preserve existing valid coverage.
3. Remove rejected patterns (innerHTML mutation, internal loading-state mutation, guessed selectors, invalid response shapes, unnecessary `of`/`tick`).
4. Add only the three prescribed tests from Codex packet:
   - controlled-pending loading + duplicate-submit prevention test,
   - success-reset test with citations,
   - transport-error-reset test.
5. Run `git diff --check`, then run `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests` once.

### Acceptance gate
- `git diff --check`
- `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
- Closure policy: `exact-gate-green + scope-clean + controller-commit`

### Avoid repeating
- Do not reintroduce trailing whitespace, brace imbalance, synthetic/manual DOM state manipulation, or selector guesses already rejected by Codex.
