# Backend ↔ Frontend Handoff — RUN_ID 20260806T174052Z

## Queue separation decision

- **Backend (PC): HOLD** on `task-07-populate-production-rag` until dependency and review constraints are satisfied.
- **Frontend (LP): CONTINUE** one bounded revise pass on `task-fe-03d-dom-state-tests`.

No cross-queue write overlap is authorized in this cycle.

## Backend package (held)

- **Level / owner:** Level 3 SURGICAL review support; PC implementation paused.
- **Task ID:** `task-07-populate-production-rag` (hierarchy package `BE-07-B`).
- **Dependency:** `BE-07-A:ACCEPTED` is required first.
- **PC allowed_paths when resumed:** `src/**`, `docs/backend/**`.
- **Exact gate when resumed:** task-07 backend compound command from `.opencode/task-plan.backend.json`.
- **Reason for hold:** current evidence shows test-failure + dirty backend task files and no current SURGICAL disposition in RUN_DIR.

## Frontend package (active)

- **Level / owner:** Level 1 LP.
- **Task ID:** `task-fe-03d-dom-state-tests` (hierarchy package `FE-03D-A`).
- **Dependency:** `task-fe-03c-citations:ACCEPTED` (already true per LP progress).
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts`.
- **Exact gate:** `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests` (after `git diff --check`).
- **Reason to continue:** gate-green but no-product-diff; Codex REVISE packet still requires concrete DOM assertion fixes.

## Integration-risk controls

1. Keep backend and frontend ownership disjoint this cycle.
2. Do not advance backend task-07 implementation while prerequisite `BE-07-A` remains unaccepted.
3. Require SURGICAL Codex review for both queues before any closure claim.
