# Backend ↔ Frontend handoff — run 20260807T005023Z

## Queue status

- **Backend (PC):** active `task-07-populate-production-rag`; gate-green request exists but closure metadata incomplete.
- **Frontend (LP):** active `task-fe-03d-dom-state-tests`; Codex REVISE packet still pending full corrective application.

## Disjoint ownership and write scopes

- **PC allowed_paths:** `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`
- **LP allowed_paths:** `frontend/**`, `docs/frontend/**` (current corrective pass narrowed to one spec file)

No write-scope overlap is required for current passes, so both queues can proceed independently.

## Coordination packages

### Package PC-07-CLOSURE
- **Level / role:** Level 2 / PC
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:** backend task chain through `task-06f-ingestion-validation` accepted
- **allowed_paths:** backend plan scope above
- **Exact gate:**
  - `git diff --check`
  - task-07 deterministic command from backend plan (full ingestion + `vector_store` row-count assertion)
  - closure policy: exact-gate-green + scope-clean + controller commit

### Package LP-FE03D-REVISE
- **Level / role:** Level 1 / LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** `task-fe-03c-citations` accepted
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate:**
  - `git diff --check`
  - `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
  - closure policy: exact-gate-green + scope-clean + controller commit

## Integration risks to watch

1. Backend queue may stall if closure metadata is not captured after a green gate.
2. Frontend queue may churn if LP diverges from the explicit Codex correction packet.
3. Cross-stack sequencing risk is low in this cycle because PC and LP changes are disjoint and no shared files are targeted.
