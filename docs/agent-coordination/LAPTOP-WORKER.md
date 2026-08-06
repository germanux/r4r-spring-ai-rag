# LP code review — run 20260806T150915Z

## Current evidence verdict
- **First current defect:** LP submitted a gate-green attempt for `task-fe-03d-dom-state-tests` with **no task-owned product diff**, and Codex explicitly returned **REVISE** with missing DOM assertion requirements.
- **Observed state in this run snapshot:**
  - Active task is `task-fe-03d-dom-state-tests` and remains PENDING (`lp-runtime/progress.json`).
  - Worker request explicitly asks for codex-revise (`worker-requests/LP.json`).
  - Checkpoint status is `no-product-diff` (`lp-runtime/checkpoint.json`).
  - Required corrective assertions are specified in `lp-runtime/codex-qwen3-extra-instructions.md`.

## Bounded next action package
- **Implementation level:** Level 1 (LP)
- **Assigned role:** LP (frontend)
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** `task-fe-03c-citations:ACCEPTED` (already satisfied per progress)
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts` (FE-03D-A constrained scope)
- **Exact gate:** `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
- **Required SURGICAL review:** Mandatory before closure (`ACCEPT` required)

### This pass instruction
Run one LP revise pass to add missing fixture-level DOM assertions (loading `[role="status"]`, disabled textarea/button while pending, `[role="alert"]` error state, visible answer/abstention text, reset clearing stale answer/error/citations), then run `git diff --check` and the exact gate.

## Acceptance conditions
1. Non-empty task-owned diff in `rag-page.component.spec.ts`.
2. `git diff --check` clean.
3. Exact gate green with full fresh evidence.
4. SURGICAL Codex `ACCEPT`.

## Avoid repeating
- Gate-only retries with no code changes.
- Claiming completion from generic green gate evidence without requirement-to-assertion mapping.
