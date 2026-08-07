# LP code review (Ring)

## Evidence reviewed

- `runtime/ring-agent/ring/20260807T002210Z/lp-runtime/progress.json`
- `runtime/ring-agent/ring/20260807T002210Z/lp-runtime/memory.md`
- `runtime/ring-agent/ring/20260807T002210Z/lp-runtime/codex-qwen3-extra-instructions.md`
- `runtime/ring-agent/ring/20260807T002210Z/lp-git-status.txt`
- `runtime/ring-agent/ring/20260807T002210Z/lp-git-diff-stat.txt`

## Current diagnosis

First current defect for LP is a **known FE-03D test correction failure** already classified by Codex REVISE.

- Active task: `task-fe-03d-dom-state-tests`.
- Latest exact gate in memory: `exit=2`.
- Current edit scope is isolated to one file: `frontend/src/app/features/rag/rag-page.component.spec.ts`.
- Correction packet is explicit and prescriptive; ambiguity is low.

## Bounded next package

- **Implementation level:** Level 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** `task-fe-03c-citations` accepted; correction packet already generated.
- **allowed_paths (canonical task scope):**
  - `frontend/**`
  - `docs/frontend/**`
- **Prescribed write target for this pass:**
  - `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Focused action (single pass):**
  1. Restore valid spec structure and remove rejected attempt-01 patterns.
  2. Add only the three prescribed DOM tests (pending loading, success reset, transport-error reset).
  3. Preserve existing accepted answer/abstention/citation/escaping/service-isolation coverage.

## Exact gates

- `git diff --check`
- `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
- Hierarchy closure: `exact-gate-green + scope-clean + controller-commit`

## Avoid repeating

- No synthetic response shapes.
- No `innerHTML` mutation.
- No manual component loading-flag mutation.
- No guessed selectors or brace/indentation regressions.
- No mismatched diagnostics versus produced patch/gate output.
