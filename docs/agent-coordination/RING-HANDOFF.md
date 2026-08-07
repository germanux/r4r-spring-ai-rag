# Backend ↔ Frontend handoff

## Queue status snapshot

- **Backend (PC):** actionable with one authorized closure retry on `task-07-populate-production-rag`.
- **Frontend (LP):** blocked by controller attempt-limit guardrail on `task-fe-03d-dom-state-tests` pending operator/controller rearm.

## Disjoint ownership and write scopes

- **PC write scope (backend):** `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`
- **LP write scope (frontend narrowed package):** `frontend/src/app/features/rag/rag-page.component.spec.ts`

No overlap exists between these scopes in current evidence, so backend closure work may proceed independently while LP remains on hold.

## Handoff packages

### Package A

- **Implementation level:** Level 2
- **Assigned role:** PC
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:** `task-06f-ingestion-validation:ACCEPTED`
- **allowed_paths:**
  - `pom.xml`
  - `src/main/**`
  - `src/test/**`
  - `docs/backend/**`
- **Exact gate:**
  1. `git diff --check`
  2. `bash -lc "rm -rf target && ./scripts/task-gate.sh all && set -a && source ./.env && set +a && mvn -q -DskipTests spring-boot:run -Dspring-boot.run.main-class=com.riansares.r4r.ingestion.KnowledgeIngestionCli && rows=$(docker exec \"${POSTGRES_APP_CONTAINER:-r4r-postgres-app}\" psql -U \"${POSTGRES_APP_USER:-r4r}\" -d \"${POSTGRES_APP_DB:-r4r_rag}\" -Atqc 'SELECT count(*) FROM vector_store') && test \"$rows\" -gt 0"`
- **Acceptance evidence:** exact-gate-green + scope-clean + controller-commit metadata.

### Package B (deferred until rearm)

- **Implementation level:** Level 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** attempt-budget reset/rearm; `task-fe-03c-citations:ACCEPTED`
- **allowed_paths:**
  - `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate:**
  1. `git diff --check`
  2. `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
- **Acceptance evidence:** exact-gate-green + scope-clean + controller-commit metadata.

## Integration risks to monitor

1. Repeated backend gate-green without controller closure can cause expensive churn with no task advancement.
2. Frontend queue remains fully blocked until attempt-budget rearm; downstream FE tasks cannot start.
3. Any accidental scope widening (PC into frontend or LP into backend/docs-backend) would violate queue disjointness.
