# LP code review (current cycle)

## Evidence reviewed

- `runtime/ring-agent/ring/20260807T002711Z/lp-runtime/gate_summary.md`
- `runtime/ring-agent/ring/20260807T002711Z/lp-runtime/codex-qwen3-extra-instructions.md`
- `runtime/ring-agent/ring/20260807T002711Z/lp-runtime/progress.json`
- `runtime/ring-agent/ring/20260807T002711Z/lp-git-status.txt`
- `runtime/ring-agent/ring/20260807T002711Z/lp-git-diff-stat.txt`

## First current defect

Task `task-fe-03d-dom-state-tests` is still failing the exact gate (`exit=2`), with one active modified file: `frontend/src/app/features/rag/rag-page.component.spec.ts`. Codex provided explicit REVISE instructions; defect is **incorrect test-shape implementation**, not missing requirements.

## Bounded next action package

- **Implementation level:** Level 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:**
  - `task-fe-03c-citations: ACCEPTED` (already satisfied in `lp-runtime/progress.json`)
  - Current Codex REVISE packet for FE-03D
- **allowed_paths:**
  - `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Focused action (single pass):**
  1. Restore valid suite structure and remove rejected patterns (synthetic responses, manual flag/DOM mutation, guessed selectors, invalid response shapes, formatting damage).
  2. Implement only the three required tests from Codex packet:
     - controlled pending loading + duplicate-submit guard,
     - independent success-reset case,
     - independent transport-error-reset case.
  3. Keep all existing valid FE-03D coverage intact.

## Exact acceptance gate

1. `git diff --check`
2. `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
3. Hierarchy closure policy: `exact-gate-green + scope-clean + controller-commit`

## Avoid repeating

- Do **not** reintroduce the previously rejected anti-patterns listed in `codex-qwen3-extra-instructions.md`.
- Do **not** widen scope beyond the single spec file.
