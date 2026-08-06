# Backend ↔ Frontend handoff (RUN 20260806T143139Z)

## Queue separation decision

- **Backend (PC): HOLD** on `task-07-populate-production-rag` until dependency readiness is explicit.
- **Frontend (LP): REVIEW** on `task-fe-03c-citations` using existing gate-green evidence.

This keeps ownership disjoint: PC does not start new backend implementation while LP resolves frontend review state.

## Backend handoff package

- **Level:** 2
- **Role:** PC
- **Task ID:** `task-07-populate-production-rag` / package `BE-07-B`
- **Dependencies:** `BE-07-A:ACCEPTED` required before execution
- **allowed_paths:** `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**` (task plan)
- **Exact gate:** task-07 command in `.opencode/task-plan.backend.json`
- **SURGICAL requirement:** mandatory ACCEPT for closure

## Frontend handoff package

- **Level:** 1
- **Role:** LP
- **Task ID:** `task-fe-03c-citations` / package `FE-03C-A`
- **Dependencies:** prior task accepted (already true)
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate:** `./scripts/frontend-task-gate.sh task-fe-03c-citations`
- **SURGICAL requirement:** mandatory ACCEPT for closure

## Integration-risk watchlist

1. **Dependency drift risk (backend):** task-07 can be executed out of order if BE-07-A acceptance is not enforced.
2. **Scope-clean risk (frontend):** LP snapshot includes non-task dirty files; closure must isolate task-owned edits.

## Immediate coordinator expectation

Prioritize SURGICAL review throughput on LP FE-03C evidence first; keep PC backend queue paused until dependency signal is authoritative.
