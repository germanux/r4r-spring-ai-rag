# LP code review (evidence-based)

## Current diagnosis

- Active task is `task-fe-03d-dom-state-tests` (`lp-runtime/progress.json`).
- Gate summary is failing (`exit code 2`) and points to local spec-file issues (`lp-runtime/gate_summary.md`).
- Codex correction packet is explicit: restore valid test-suite structure, remove prohibited patterns, and add only three prescribed DOM tests (`lp-runtime/codex_plan.json`, `lp-runtime/codex-qwen3-extra-instructions.md`).
- Current diff is bounded to one file with significant edits (`lp-git-diff-stat.txt`):
  - `frontend/src/app/features/rag/rag-page.component.spec.ts`

First current defect for LP is **local FE-03D test-file correctness**, not architecture or cross-file scope.

## Bounded next package

- **Implementation level:** Level 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** `task-fe-03c-citations` accepted (visible in `lp-runtime/progress.json`)
- **allowed_paths (canonical):** `frontend/**`, `docs/frontend/**` (configured task plan); execute correction only in `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Next action (single pass):**
  1. Restore valid suite structure and remove flagged defective additions.
  2. Add only the three required tests:
     - controlled-pending loading/duplicate-submit DOM test,
     - success-reset DOM test,
     - transport-error-reset DOM test.
  3. Run precheck and exact gate once.

## Exact gate

1. `git diff --check`
2. `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
3. Closure rule: `exact-gate-green + scope-clean + controller-commit`

## Acceptance evidence required

- No trailing whitespace / parse-structure defects.
- Exit 0 for FE-03D gate.
- Final diff remains task-scoped and preserves existing valid coverage (answer, abstention, citations, transport alert, escaping, isolation).

## Avoid repeating

- Do **not** reintroduce `innerHTML` mutation, internal loading-state mutation, guessed selectors, unnecessary `of/tick`, synthetic invalid response shapes, or unbalanced braces.
