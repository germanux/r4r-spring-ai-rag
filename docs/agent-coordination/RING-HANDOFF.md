# Backend ↔ Frontend handoff

## Queue status in this cycle

- **Backend (PC):** `task-07-populate-production-rag` is active with gate-green execution evidence but incomplete closure metadata.
- **Frontend (LP):** `task-fe-03d-dom-state-tests` is active with failing gate evidence and a bounded Codex revise packet.

## Ownership and write-scope separation

Disjoint scopes are preserved; concurrent progress is safe:

- **PC allowed_paths:** `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`
- **LP allowed_paths:** `frontend/**`, `docs/frontend/**` (effective correction scope currently one file under `frontend/src/app/features/rag/`)

No current overlap is evidenced between active backend and frontend packages.

## Directed next actions

### Package A
- **Implementation level:** Level 2
- **Assigned role:** PC
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:** `task-06f-ingestion-validation: ACCEPTED`
- **allowed_paths:** backend task-07 plan scope
- **Exact gate:**
  - `git diff --check`
  - `bash -lc "rm -rf target && ./scripts/task-gate.sh all && set -a && source ./.env && set +a && mvn -q -DskipTests spring-boot:run -Dspring-boot.run.main-class=com.riansares.r4r.ingestion.KnowledgeIngestionCli && rows=$(docker exec \"${POSTGRES_APP_CONTAINER:-r4r-postgres-app}\" psql -U \"${POSTGRES_APP_USER:-r4r}\" -d \"${POSTGRES_APP_DB:-r4r_rag}\" -Atqc 'SELECT count(*) FROM vector_store') && test \"$rows\" -gt 0"`

### Package B
- **Implementation level:** Level 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** `task-fe-03c-citations: ACCEPTED`
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate:**
  - `git diff --check`
  - `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`

## Integration risks to monitor

1. Backend queue can appear stalled if closure metadata remains null after green gate runs.
2. Frontend FE-03D can regress stable prior tests if LP applies corrections beyond the prescribed one-file packet.
