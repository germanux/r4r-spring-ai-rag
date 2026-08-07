# Backend ↔ Frontend handoff — run 20260807T013028Z

## Concurrency and scope separation

- **PC (backend)** remains on `task-07-populate-production-rag`.
  - Scope: `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`.
- **LP (frontend)** remains on `task-fe-03d-dom-state-tests`.
  - Scope for this pass: `frontend/src/app/features/rag/rag-page.component.spec.ts`.

These scopes are disjoint for this cycle; continue both queues in parallel without overlap hold.

## Current cross-stack status

- Backend: gate summary is green but closure metadata/evidence is incomplete for task acceptance.
- Frontend: deterministic gate is red; single-file spec correction is still required.

## Directed next actions

### Action A
- **Implementation level:** Level 2
- **Role:** PC
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:** `task-06f-ingestion-validation: ACCEPTED`
- **allowed_paths:** `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`
- **Exact gate:**
  - `git diff --check`
  - `bash -lc "rm -rf target && ./scripts/task-gate.sh all && set -a && source ./.env && set +a && mvn -q -DskipTests spring-boot:run -Dspring-boot.run.main-class=com.riansares.r4r.ingestion.KnowledgeIngestionCli && rows=$(docker exec \"${POSTGRES_APP_CONTAINER:-r4r-postgres-app}\" psql -U \"${POSTGRES_APP_USER:-r4r}\" -d \"${POSTGRES_APP_DB:-r4r_rag}\" -Atqc 'SELECT count(*) FROM vector_store') && test \"$rows\" -gt 0"`

### Action B
- **Implementation level:** Level 1
- **Role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** `task-fe-03c-citations: ACCEPTED`
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate:**
  - `git diff --check`
  - `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`

## Integration risks to monitor

1. PC can loop in BLOCKED state if gate-green submissions continue without closure-complete diagnostics.
2. LP failure on FE-03D blocks downstream frontend tasks (`task-fe-03e`, `task-fe-03f`, `task-fe-04`).
3. Any write-scope drift will cause supervisor rejection despite functional progress.
