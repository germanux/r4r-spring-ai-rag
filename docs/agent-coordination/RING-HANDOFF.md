# Backend ↔ Frontend handoff (Ring)

## Queue separation decision
- **Backend (PC): HOLD** on `task-07-populate-production-rag` until dependency unblocks.
- **Frontend (LP): CONTINUE** on `task-fe-03d-dom-state-tests` with a single-file revise pass.

This keeps backend/frontend ownership disjoint and avoids overlapping write scopes in the next pass.

## Backend package
- **Level / role:** Level 2 / PC
- **Task ID:** `task-07-populate-production-rag` (`BE-07-B`)
- **Dependencies:** `BE-07-A:ACCEPTED` (not yet proven)
- **allowed_paths:** `src/**`, `docs/backend/**`
- **Exact gate:** `./scripts/task-gate.sh all`
- **SURGICAL review requirement:** required before closure

## Frontend package
- **Level / role:** Level 1 / LP
- **Task ID:** `task-fe-03d-dom-state-tests` (`FE-03D-A`)
- **Dependencies:** `task-fe-03c-citations:ACCEPTED` (satisfied)
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate:** `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
- **SURGICAL review requirement:** required before closure

## Integration-risk watch
1. Resuming backend task-07 before BE-07-A acceptance risks repeated blocked runs and non-actionable backend churn.
2. Advancing frontend without strict DOM-level assertions risks false confidence in FE-03D behavior proof.

## Handoff contract for next cycle
- Do not dispatch PC execution until BE-07-A acceptance evidence appears in a newer snapshot.
- Dispatch LP for one revise pass only, with preflight `git diff --check` and exact gate rerun.
