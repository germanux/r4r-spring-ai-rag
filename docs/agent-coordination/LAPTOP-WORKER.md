# LP code review — run 20260807T011526Z

## Evidence reviewed

- `runtime/ring-agent/ring/20260807T011526Z/lp-runtime/gate_summary.md`
- `runtime/ring-agent/ring/20260807T011526Z/lp-runtime/codex_plan.json`
- `runtime/ring-agent/ring/20260807T011526Z/lp-runtime/codex-qwen3-extra-instructions.md`
- `runtime/ring-agent/ring/20260807T011526Z/lp-git-status.txt`
- `runtime/ring-agent/ring/20260807T011526Z/lp-git-diff-stat.txt`

## Current diagnosis

The active LP task `task-fe-03d-dom-state-tests` has a deterministic gate failure (`exit 2`) and a single edited test file. Codex guidance is specific: restore valid suite structure and remove prohibited patterns before re-running the exact FE gate.

## Bounded next action package

- **Implementation level:** Level 1  
- **Assigned role:** LP  
- **Task ID:** `task-fe-03d-dom-state-tests`  
- **Dependencies:** `task-fe-03c-citations:ACCEPTED` (already satisfied in progress evidence)  
- **allowed_paths (canonical write scope):** `frontend/src/app/features/rag/rag-page.component.spec.ts`

### One-pass objective

In one bounded edit pass on `rag-page.component.spec.ts` only:

1. Restore valid test-suite structure and remove malformed/prohibited additions.
2. Add exactly the prescribed DOM tests:
   - controlled-pending loading + duplicate-submit guard,
   - success-reset cleanup,
   - transport-error-reset cleanup.
3. Keep existing valid answer/abstention/citation/escaping/isolation coverage intact.
4. Run:
   - `git diff --check`
   - `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`

### Exact acceptance gate

- `git diff --check` clean (no trailing whitespace).
- FE-03D gate exits 0.
- Closure policy satisfied: `exact-gate-green + scope-clean + controller-commit`.

## Avoid repeating

Do not reintroduce prior failure patterns: unbalanced braces, invalid response shapes, guessed selectors, internal-state mutation, `innerHTML` mutation, or unnecessary `of`/`tick` usage.
