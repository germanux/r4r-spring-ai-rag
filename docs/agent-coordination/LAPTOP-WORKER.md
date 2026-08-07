# LP code review (frontend)

## Evidence reviewed

- `runtime/ring-agent/ring/20260807T011026Z/lp-runtime/gate_summary.md`
- `runtime/ring-agent/ring/20260807T011026Z/lp-runtime/codex_plan.json`
- `runtime/ring-agent/ring/20260807T011026Z/lp-runtime/codex-qwen3-extra-instructions.md`
- `runtime/ring-agent/ring/20260807T011026Z/lp-git-status.txt`
- `runtime/ring-agent/ring/20260807T011026Z/lp-git-diff-stat.txt`

## Current diagnosis

Task `task-fe-03d-dom-state-tests` is still failing (`gate-failure`, exit code 2). Codex READY instructions point to a local defect in one file: trailing whitespace plus malformed test-suite structure and prohibited test patterns.

## Bounded next work package

- **Implementation level:** Level 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** `task-fe-03c-citations` accepted (already satisfied)
- **allowed_paths (canonical write scope):**
  - `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **One observable change set:** restore valid suite structure and implement only the three prescribed DOM tests from Codex plan.

## Exact gate and acceptance conditions

1. `git diff --check`
2. `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
3. Closure policy check: exact gate green + scope clean + controller-owned commit

## Required assertion targets in this pass

- Loading status: `.loading-state[role="status"]`
- Disabled controls: `textarea` and `.submit-button`
- Success render and reset: `.answer-content`, `.citations-section`, `.idle-state`
- Error render and reset: `.error-state[role="alert"]`, then absence after `clear()`

## Avoid repeating

Do not reintroduce invalid suite structure, trailing whitespace, guessed `data-testid` selectors, `innerHTML` mutation, internal-state mutation (`component.isLoading` / invented state), or unnecessary `of`/`tick` usage.
