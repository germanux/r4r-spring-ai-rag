# Backend ↔ Frontend handoff (Ring)

## Queue status

- **PC (backend):** `task-07-populate-production-rag` remains active; evidence indicates gate-green request but incomplete closure proof.
- **LP (frontend):** `task-fe-03d-dom-state-tests` remains active; evidence indicates revise-required spec correction.

## Disjoint execution plan

### Package A
- **Implementation level:** Level 2
- **Owner:** PC
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:** backend chain through `task-06f-ingestion-validation:ACCEPTED`
- **allowed_paths:** `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`
- **Exact gate:**
  - `git diff --check`
  - task-07 gate command from backend plan (full deterministic command)

### Package B
- **Implementation level:** Level 1
- **Owner:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** `task-fe-03c-citations:ACCEPTED`
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate:**
  - `git diff --check`
  - `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`

## Overlap and risk check

- No write-scope overlap between Package A and B.
- Primary integration risk is schedule coupling only: frontend can continue independently; backend closure evidence should not block LP correction.

## Handoff rule

Proceed with both packages in parallel advisory flow; hold only if a new evidence-backed scope overlap appears.
