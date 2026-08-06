# LP code review (evidence cycle 20260806T184628Z)

## Current evidence read
- `lp-runtime/progress.json`: active task `task-fe-03d-dom-state-tests` remains `PENDING`.
- `lp-runtime/memory.md`: latest gate exit is `2`; latest Codex decision is `REVISE`.
- `lp-runtime/codex-qwen3-extra-instructions.md`: explicit corrective recipe for replacing defective synthetic tests and restoring selector-based DOM assertions.
- `lp-runtime/gate_summary.md`: deterministic gate failure captured for current pass.
- `lp-git-diff-stat.txt`: substantial spec-file churn without proof of accepted correction.

## First current defect (LP)
The FE-03D patch did not satisfy the deterministic gate and Codex review contract. Evidence points to incorrect/synthetic test additions and inconsistent packaging versus required selector-level assertions.

## Bounded next action package
- **Implementation level:** 1 (LP)
- **Assigned role:** LP worker
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** `task-fe-03c-citations:ACCEPTED` (already met)
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts` (FE-03D-A scope)
- **Exact gate:**
  1. `git diff --check`
  2. `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
- **Required SURGICAL review:** mandatory after gate-green before closure.

## Concrete correction to execute now
1. Remove synthetic newly added tests called out by Codex.
2. Restore one controlled-pending loading test that asserts:
   - `.loading-state[role="status"]` text,
   - disabled `textarea`,
   - disabled `.submit-button`,
   - second submit while pending does not create another service call.
3. Split reset validation into two independent rendered-DOM tests:
   - success-then-clear,
   - transport-error-then-clear.
4. Keep existing valid coverage intact and avoid invalid types/fields/DOM mutation shortcuts.

## Acceptance evidence expected next
- Final gate artifact reports exit `0` for FE-03D.
- changed-paths scope remains exactly the spec file (plus controller-owned memory/progress metadata).
- Local understanding maps each FE-03D requirement to exact selector + assertion.

## Avoid repeating
- Do not reintroduce `innerHTML` mutation, fake response types/fields, unnecessary async utilities, or mixed/contradictory evidence from different executions.
