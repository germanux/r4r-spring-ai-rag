# LP code review — run 20260806T192132Z

## Current evidence

- Active frontend task is `task-fe-03d-dom-state-tests` (`lp-runtime/progress.json`).
- Latest deterministic gate is red (`lp-runtime/gate_summary.md`, exit `2`).
- LP memory records Codex `REVISE` and a prescriptive correction packet focused on `rag-page.component.spec.ts` (`lp-runtime/memory.md`).

## First current defect

The first defect is in the current LP patch behavior: synthetic/invalid DOM tests were introduced and did not satisfy FE-03D deterministic assertions, leaving the gate red.

## Bounded next action package

- **Implementation level:** 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:**
  - `task-fe-03c-citations:ACCEPTED`
  - Existing Codex correction constraints captured in LP memory/directive
- **allowed_paths:**
  - Canonical task scope: `frontend/**`, `docs/frontend/**` (from `.opencode/task-plan.frontend.json`)
  - **This pass constrained to:** `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate / constraint:**
  - `git diff --check`
  - `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
  - Closure policy: `exact-gate-green + scope-clean + surgical-accept + controller-commit`
- **Required SURGICAL review:** mandatory after LP gate-green result

## Acceptance evidence required

1. LP publishes one coherent diff limited to the spec file above.
2. Deterministic FE-03D gate is green for that exact pass.
3. Diagnostics are internally consistent (manifest, task-gate output, and gate summary refer to the same final run).
4. SURGICAL returns `ACCEPT` before controller closeout.

## Avoid repeating

Do **not** re-add synthetic tests, invalid state shapes, direct `innerHTML` mutation, or mixed diagnostics from non-final runs.
