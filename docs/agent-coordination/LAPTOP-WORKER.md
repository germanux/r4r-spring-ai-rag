# LP code review (evidence cycle: 20260806T174553Z)

## Current evidence read
- `lp-runtime/progress.json`: active task `task-fe-03d-dom-state-tests` remains `PENDING`.
- `lp-runtime/gate_summary.md`: gate is green (`exit 0`).
- `worker-request-manifest.json` and `worker-requests/LP.json`: Codex decision is `REVISE` with explicit next action.
- `lp-runtime/checkpoint.json`: checkpoint status is `no-product-diff`.
- `lp-git-status.txt` + `lp-git-diff-stat.txt`: only memory file is dirty in current snapshot.
- `lp-runtime/codex-qwen3-extra-instructions.md`: concrete selector/assertion corrections remain mandatory.

## First current defect
LP produced a green gate without a material product diff for the required spec updates. Codex explicitly requested REVISE because required loading/reset DOM assertions and requirement mapping were not delivered as a concrete scoped patch.

## Bounded next action package
- **Implementation level:** Level 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests` (work package anchor: `FE-03D-A`)
- **Dependencies:** `task-fe-03c-citations:ACCEPTED` (already satisfied in progress evidence)
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts` only
- **Exact gate:** `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
- **Pre-gate hygiene:** `git diff --check`
- **Required SURGICAL review:** Codex `ACCEPT` required before task closure

## Required content of the LP patch (non-negotiable)
1. Loading-state assertions via `.loading-state[role="status"]`, `textarea`, `.submit-button` after submit + detectChanges.
2. Assert both controls are disabled while request is pending.
3. One extra `component.onSubmit()` call while pending and assert `ragApiService.query` total calls remain exactly one.
4. Split reset logic into independent success-reset and transport-error-reset tests.
5. Before/after clear assertions for answer/citations/error/idle states exactly as Codex packet specifies.
6. Preserve existing answer/abstention/citation/transport-alert/service-isolation coverage.
7. Deliver a non-empty scoped patch and a requirement-to-selector/assertion mapping in understanding evidence.

## Avoid repeating
- Do not submit a memory-only update.
- Do not submit another gate-green + no-product-diff attempt.
- Do not widen scope outside the single spec file.
