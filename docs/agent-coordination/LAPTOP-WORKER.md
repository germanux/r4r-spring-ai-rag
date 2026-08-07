# LP code review — run 20260807T013028Z

## Current evidence read

- `runtime/ring-agent/ring/20260807T013028Z/lp-runtime/progress.json`
- `runtime/ring-agent/ring/20260807T013028Z/lp-runtime/gate_summary.md`
- `runtime/ring-agent/ring/20260807T013028Z/lp-runtime/codex_plan.json`
- `runtime/ring-agent/ring/20260807T013028Z/lp-runtime/codex-qwen3-extra-instructions.md`
- `runtime/ring-agent/ring/20260807T013028Z/lp-git-status.txt`

## Diagnosis (first current defect)

LP remains on `task-fe-03d-dom-state-tests` with deterministic gate failure (`exit 2`). The Codex correction packet already narrows the defect to a single file and prescribes exact repairs; this indicates unfinished local correction, not cross-stack ambiguity.

## Bounded next package

- **Implementation level:** Level 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** `task-fe-03c-citations: ACCEPTED` (already satisfied in progress ledger)
- **allowed_paths (canonical write scope):**
  - `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **One focused action for one pass:**
  1. Restore valid suite structure and remove forbidden attempt patterns called out by Codex.
  2. Keep only the prescribed DOM tests (controlled pending loading + success reset + transport-error reset).
  3. Run `git diff --check`, then run the exact FE-03D gate once.

## Exact gate

1. `git diff --check`
2. `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
3. Hierarchy closure policy: `exact-gate-green + scope-clean + controller-commit`

## Acceptance evidence required

- Non-empty, single-file scoped patch in `rag-page.component.spec.ts`.
- Gate exit code `0` for FE-03D.
- Consistent diagnostics and understanding report tied to selectors/assertions requested in Codex instructions.

## Avoid repeating

Do **not** reintroduce malformed braces/indentation, trailing whitespace, synthetic response fields, internal state mutation, guessed selectors, or unnecessary `of`/`tick` usage already rejected.
