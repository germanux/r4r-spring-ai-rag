# LP code review — run 20260806T172722Z

## Current evidence reviewed
- `runtime/ring-agent/ring/20260806T172722Z/lp-runtime/progress.json`
- `runtime/ring-agent/ring/20260806T172722Z/lp-runtime/gate_summary.md`
- `runtime/ring-agent/ring/20260806T172722Z/lp-runtime/checkpoint.json`
- `runtime/ring-agent/ring/20260806T172722Z/lp-runtime/codex-qwen3-extra-instructions.md`
- `runtime/ring-agent/ring/20260806T172722Z/lp-runtime/local_understanding.md`
- `runtime/ring-agent/ring/20260806T172722Z/worker-requests/LP.json`

## First current defect (LP)
The latest deterministic gate is green, but the attempt is not closable:
- Codex decision is `REVISE` with explicit missing assertions and structure corrections.
- Checkpoint status is `no-product-diff` (`changed_paths: []`).
- Local understanding does not map requirements to concrete DOM assertions.

This is a **correction-before-new-work** situation.

## Decision
- **Action:** `CONTINUE`
- **Active task ID:** `task-fe-03d-dom-state-tests`
- **Why now:** task remains pending; required DOM assertion corrections are prescribed and bounded to one test file.

## Bounded next action package
- **Implementation level:** Level 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests` (work package `FE-03D-A`)
- **Dependencies:** `task-fe-03c-citations:ACCEPTED` (already satisfied per progress evidence)
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate:**
  1. `git diff --check`
  2. `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
- **Required SURGICAL review:** mandatory Codex `ACCEPT` after gate-green evidence and non-empty scoped patch.

## Mandatory assertions to implement in this pass (from Codex packet)
1. Loading-state DOM assertions must query `.loading-state[role="status"]`, `textarea`, and `.submit-button`, and verify visible loading plus rendered-control `disabled === true`.
2. Pending duplicate-submit guard must call `component.onSubmit()` exactly once more while first request is pending, and assert `ragApiService.query` total calls remains exactly one.
3. Replace combined reset test with two independent tests:
   - success-reset path (answer/citations present before clear; absent after clear; idle restored),
   - transport-error-reset path (`role="alert"` present before clear; absent after clear; idle restored).
4. Preserve existing answer/abstention/citation/transport-alert/service-isolation coverage.
5. Produce an explicit requirement→DOM query/assertion mapping in local understanding evidence.

## Avoid repeating
Do **not** submit another gate-green/no-product-diff cycle or memory-only requirement mapping.
