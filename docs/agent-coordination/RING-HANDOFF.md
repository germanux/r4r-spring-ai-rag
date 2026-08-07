# Backend ↔ Frontend handoff

## Concurrency decision

Proceed with both queues in parallel; scopes are disjoint by plan and current evidence.

- **PC (backend):** `task-07-populate-production-rag`
  - Level 2, role PC
  - allowed_paths: `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`
- **LP (frontend):** `task-fe-03d-dom-state-tests`
  - Level 1, role LP
  - allowed_paths: `frontend/**`, `docs/frontend/**`

No overlap is present between backend and frontend write scopes.

## Backend package

- **Task ID:** `task-07-populate-production-rag`
- **Dependency:** `task-06f-ingestion-validation:ACCEPTED` (already satisfied in progress ledger)
- **Focused pass:** closure-quality evidence completion, not architectural expansion.
- **Exact gate:**
  1. `git diff --check`
  2. `bash -lc "rm -rf target && ./scripts/task-gate.sh all && set -a && source ./.env && set +a && mvn -q -DskipTests spring-boot:run -Dspring-boot.run.main-class=com.riansares.r4r.ingestion.KnowledgeIngestionCli && rows=$(docker exec \"${POSTGRES_APP_CONTAINER:-r4r-postgres-app}\" psql -U \"${POSTGRES_APP_USER:-r4r}\" -d \"${POSTGRES_APP_DB:-r4r_rag}\" -Atqc 'SELECT count(*) FROM vector_store') && test \"$rows\" -gt 0"`

## Frontend package

- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependency:** `task-fe-03c-citations:ACCEPTED` (already satisfied in progress ledger)
- **Focused pass:** one-file spec correction from active Codex REVISE packet.
- **Exact gate:**
  1. `git diff --check`
  2. `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`

## Integration risks to watch

1. **False closure risk (backend):** gate-green without complete closure metadata can stall acceptance.
2. **Spec churn risk (frontend):** repeated broad edits in the same spec file can regress previously accepted coverage.

## Handoff rule

If either queue reports first-failure ambiguity beyond its bounded file/layer scope, hold only that queue and keep the other queue moving.
