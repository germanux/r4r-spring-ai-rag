# Backend ↔ Frontend handoff

## Queue status snapshot

- **Backend (PC):** `task-07-populate-production-rag` is gate-green but still blocked at closure (`CHECKPOINT_COMMIT_FAILED`).
- **Frontend (LP):** `task-fe-03d-dom-state-tests` remains red with a bounded single-file correction pending.

## Concurrency and scope safety

No write-scope overlap detected for the next pass:

- PC scope: `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`
- LP scope: `frontend/src/app/features/rag/rag-page.component.spec.ts` (within `frontend/**`)

These are disjoint backend/frontend paths, so both queues may continue in parallel.

## Bounded next actions

### PC package

- **Level:** 2
- **Role:** PC
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:** `task-06f-ingestion-validation` accepted
- **allowed_paths:** `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`
- **Exact gate:**
  - `git diff --check`
  - task-07 exact backend command from `.opencode/task-plan.backend.json`
  - closure policy `exact-gate-green + scope-clean + controller-commit`

### LP package

- **Level:** 1
- **Role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** `task-fe-03c-citations` accepted
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate:**
  - `git diff --check`
  - `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
  - closure policy `exact-gate-green + scope-clean + controller-commit`

## Integration risks to watch

1. Backend closure may continue to stall if evidence metadata is incomplete even when gate output is green.
2. Frontend FE-03D can loop if LP reintroduces previously rejected spec patterns instead of following the current correction packet exactly.
