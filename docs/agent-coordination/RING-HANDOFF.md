# Backend ↔ Frontend handoff

## Queue status split

- **Backend (PC):** `task-07-populate-production-rag` remains active; gate-green request exists but closure proof is incomplete in current run metadata.
- **Frontend (LP):** `task-fe-03d-dom-state-tests` remains active with gate failure exit `2` and explicit Codex REVISE instructions.

These are disjoint write scopes in this cycle:

- PC scope: backend Java/tests/docs (`pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`)
- LP scope: one frontend spec file (`frontend/src/app/features/rag/rag-page.component.spec.ts`)

No overlap blocker is present; both queues can continue independently.

## Coordinated risk notes

1. **Backend closure-risk:** If PC does not preserve deterministic row-count/idempotence evidence with the exact gate artifacts, task-07 may remain administratively blocked despite green execution.
2. **Frontend regression-risk:** FE-03D can keep failing if LP mixes prior defective insertions with new assertions instead of first restoring valid syntax/structure.

## Current pass directives

### Package A
- **Implementation level:** Level 2
- **Role:** PC
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:** `task-06f-ingestion-validation:ACCEPTED`
- **allowed_paths:** `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`
- **Exact gate:**
  - `git diff --check`
  - `bash -lc "rm -rf target && ./scripts/task-gate.sh all && set -a && source ./.env && set +a && mvn -q -DskipTests spring-boot:run -Dspring-boot.run.main-class=com.riansares.r4r.ingestion.KnowledgeIngestionCli && rows=$(docker exec \"${POSTGRES_APP_CONTAINER:-r4r-postgres-app}\" psql -U \"${POSTGRES_APP_USER:-r4r}\" -d \"${POSTGRES_APP_DB:-r4r_rag}\" -Atqc 'SELECT count(*) FROM vector_store') && test \"$rows\" -gt 0"`
  - `exact-gate-green + scope-clean + controller-commit`

### Package B
- **Implementation level:** Level 1
- **Role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** `task-fe-03c-citations:ACCEPTED`
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate:**
  - `git diff --check`
  - `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
  - `exact-gate-green + scope-clean + controller-commit`
