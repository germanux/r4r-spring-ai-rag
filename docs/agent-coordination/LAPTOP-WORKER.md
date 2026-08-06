# LP code review — run 20260806T192632Z

## Current evidence reviewed

- Active frontend task is `task-fe-03d-dom-state-tests` (`lp-runtime/progress.json`).
- Latest deterministic frontend gate is red (`lp-runtime/gate_summary.md`, exit `2`).
- LP memory carries prescriptive REVISE constraints targeting synthetic/invalid tests in `rag-page.component.spec.ts` (`lp-runtime/memory.md`).

## First current defect

The first defect is in the current LP patch behavior: introduced synthetic/invalid DOM tests did not satisfy FE-03D assertions and left the deterministic gate red.

## Bounded next action package

- **Implementation level:** 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:**
  - `task-fe-03c-citations:ACCEPTED`
  - Existing Codex correction packet in `lp-runtime/memory.md`
- **allowed_paths:**
  - Canonical task scope from `.opencode/task-plan.frontend.json`: `frontend/**`, `docs/frontend/**`
  - **This correction pass only:** `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate:**
  - `git diff --check`
  - `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
- **Required SURGICAL review:** Yes, after a gate-green LP pass.

## Acceptance conditions and required evidence

1. One coherent diff limited to the single spec file above.
2. FE-03D deterministic gate turns green on that exact pass.
3. Diagnostics are internally consistent (manifest/task-gate/gate summary all describe the same final run).
4. SURGICAL returns `ACCEPT` before controller closeout.

## Avoid repeating

Do **not** re-add synthetic tests, fake success fields, invalid state values, direct `innerHTML` mutation, or mixed diagnostics from non-final runs.
