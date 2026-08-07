# Backend ↔ Frontend handoff (run 20260807T021032Z)

## Scope and concurrency decision

Proceed PC and LP concurrently this cycle. Active scopes are disjoint and no overlap is evidenced.

- **PC scope:** `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`
- **LP scope:** `frontend/src/app/features/rag/rag-page.component.spec.ts`

## Backend package

- **Implementation level:** Level 2
- **Assigned role:** PC
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:** `task-06f-ingestion-validation: ACCEPTED`
- **allowed_paths:** `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`
- **Focused action:** one closure-quality pass only; complete non-null closure metadata and explicit `vector_store` count evidence.
- **Exact gate:**
  1. `git diff --check`
  2. `bash -lc "rm -rf target && ./scripts/task-gate.sh all && set -a && source ./.env && set +a && mvn -q -DskipTests spring-boot:run -Dspring-boot.run.main-class=com.riansares.r4r.ingestion.KnowledgeIngestionCli && rows=$(docker exec \"${POSTGRES_APP_CONTAINER:-r4r-postgres-app}\" psql -U \"${POSTGRES_APP_USER:-r4r}\" -d \"${POSTGRES_APP_DB:-r4r_rag}\" -Atqc 'SELECT count(*) FROM vector_store') && test \"$rows\" -gt 0"`
  3. `Closure policy: exact-gate-green + scope-clean + controller-commit`
- **Acceptance condition:** gate exit 0 + row-count proof + non-null closure metadata fields.

## Frontend package

- **Implementation level:** Level 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** `task-fe-03c-citations: ACCEPTED`
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Focused action:** apply only the FE-03D one-file correction packet and keep valid existing coverage.
- **Exact gate:**
  1. `git diff --check`
  2. `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
  3. `Closure policy: exact-gate-green + scope-clean + controller-commit`
- **Acceptance condition:** FE-03D gate green with one-file clean scope and selector-mapped DOM assertions.

## Integration risks to monitor next cycle

1. Backend closure can stall again if gate-green evidence is submitted without non-null closure metadata.
2. Frontend churn can continue if FE-03D packet constraints are partially applied.
