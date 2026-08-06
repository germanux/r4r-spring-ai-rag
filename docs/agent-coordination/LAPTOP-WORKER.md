# LP code review (evidence cycle: 20260806T184128Z)

## Current evidence read
- `lp-runtime/progress.json`: active task `task-fe-03d-dom-state-tests` is still `PENDING`.
- `lp-runtime/memory.md`: latest gate is `exit=2`; latest Codex decision is `REVISE`; next action is explicit FE-03D test rewrite guidance.
- `lp-runtime/gate_summary.md`: current packaged gate classification is `gate-failure` with exit `2`.
- `lp-runtime/codex-qwen3-extra-instructions.md`: contains mandatory selector-level corrections and evidence consistency requirements.
- `lp-git-status.txt`: product diff currently includes `frontend/src/app/features/rag/rag-page.component.spec.ts` plus memory.

## First current defect
LP has not yet delivered the Codex-mandated FE-03D correction set as an accepted, gate-green, surgically-approved packet. The queue is still on a revise loop and must execute one tightly bounded fix pass.

## Bounded next action package
- **Implementation level:** Level 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests` (work-package anchor `FE-03D-A`)
- **Dependencies:** `task-fe-03c-citations:ACCEPTED` (already satisfied)
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts` only
- **Exact gate:** `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
- **Pre-gate hygiene:** `git diff --check`
- **Required SURGICAL review:** Codex `ACCEPT` is mandatory before closure

## Required correction content (from current Codex packet)
1. Restore one controlled pending-observable loading test with `.loading-state[role="status"]`, `textarea`, and `.submit-button` assertions.
2. Assert both rendered controls are disabled while pending.
3. Call `component.onSubmit()` exactly once more while pending; keep total `ragApiService.query` calls at one.
4. Split reset checks into two independent tests:
   - success reset path (answer/citations present before clear; answer/citations/error absent and idle present after clear),
   - transport-error reset path (alert present before clear; alert absent and idle present after clear).
5. Use valid existing Subject-based infrastructure and project types; avoid synthetic invalid state fields or direct DOM mutation patterns rejected by Codex.

## Avoid repeating
- Do not submit stale/inconsistent diagnostics where gate summary, full log, and task-gate metadata describe different executions.
- Do not widen scope outside the single FE-03D spec file.
