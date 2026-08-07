# Backend ↔ Frontend handoff (disjoint execution)

## Scope separation check

- **PC active scope (backend):** `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`
- **LP active scope (frontend):** `frontend/src/app/features/rag/rag-page.component.spec.ts`

Current scopes are disjoint; concurrent PC/LP work is safe without write-path overlap.

## Backend package to execute

- **Level:** 2
- **Role:** PC
- **Task:** `task-07-populate-production-rag`
- **Dependencies:** `task-06f-ingestion-validation:ACCEPTED`
- **allowed_paths:** `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`
- **Exact gate:**
  1. `git diff --check`
  2. `bash -lc "rm -rf target && ./scripts/task-gate.sh all && set -a && source ./.env && set +a && mvn -q -DskipTests spring-boot:run -Dspring-boot.run.main-class=com.riansares.r4r.ingestion.KnowledgeIngestionCli && rows=$(docker exec \"${POSTGRES_APP_CONTAINER:-r4r-postgres-app}\" psql -U \"${POSTGRES_APP_USER:-r4r}\" -d \"${POSTGRES_APP_DB:-r4r_rag}\" -Atqc 'SELECT count(*) FROM vector_store') && test \"$rows\" -gt 0"`

## Frontend package to execute

- **Level:** 1
- **Role:** LP
- **Task:** `task-fe-03d-dom-state-tests`
- **Dependencies:** `task-fe-03c-citations:ACCEPTED`
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate:**
  1. `git diff --check`
  2. `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`

## Integration risks to track

1. Backend closure loop risk: repeated gate-green with incomplete closure artifacts can keep task-07 blocked.
2. Frontend test fragility risk: FE-03D can fail pre-runtime on formatting/structure before semantic DOM assertions run.

## Coordination rule for this cycle

Proceed with both packages in parallel lanes; do not hold either lane for SURGICAL review (disabled) or absent ACCEPT/REVISE metadata.
