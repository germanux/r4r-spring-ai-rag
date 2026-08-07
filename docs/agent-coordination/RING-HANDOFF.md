# Backend ↔ Frontend handoff

## Concurrency and scope safety

PC and LP can proceed concurrently in this cycle; write scopes are disjoint:

- **PC (backend task-07):** `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`
- **LP (frontend FE-03D):** `frontend/src/app/features/rag/rag-page.component.spec.ts`

No overlapping write scope is evidenced in current directives/snapshots.

## Backend handoff (PC)

- **Implementation level:** 2
- **Assigned role:** PC
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:** `task-06f-ingestion-validation` accepted; backend phase active
- **Exact gate:**
  - `git diff --check`
  - `bash -lc "rm -rf target && ./scripts/task-gate.sh all && set -a && source ./.env && set +a && mvn -q -DskipTests spring-boot:run -Dspring-boot.run.main-class=com.riansares.r4r.ingestion.KnowledgeIngestionCli && rows=$(docker exec \"${POSTGRES_APP_CONTAINER:-r4r-postgres-app}\" psql -U \"${POSTGRES_APP_USER:-r4r}\" -d \"${POSTGRES_APP_DB:-r4r_rag}\" -Atqc 'SELECT count(*) FROM vector_store') && test \"$rows\" -gt 0"`
- **Acceptance evidence:** non-null closure metadata plus explicit row-count proof.

## Frontend handoff (LP)

- **Implementation level:** 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** `task-fe-03c-citations` accepted; frontend phase active
- **Exact gate:**
  - `git diff --check`
  - `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
- **Acceptance evidence:** FE-03D gate green with one-file scoped diff and selector-mapped DOM assertions.

## Integration risks to monitor next cycle

1. PC may continue producing gate-green requests that cannot close if closure metadata fields remain null.
2. LP may continue red-gate churn if the correction packet constraints are only partially applied in the spec file.
