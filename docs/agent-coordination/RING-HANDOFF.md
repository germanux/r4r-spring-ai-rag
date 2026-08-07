# Backend ↔ Frontend handoff (current cycle)

## Queue status
- **Backend (PC):** continue `task-07-populate-production-rag` to resolve closure failure after gate-green evidence.
- **Frontend (LP):** continue `task-fe-03d-dom-state-tests` with one-file corrective pass from Codex packet.

## Scope overlap check
- PC write scope: backend/doc paths (`pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`).
- LP write scope: frontend/doc paths (`frontend/**`, `docs/frontend/**`), with explicit one-file focus.
- **Result:** no overlap; safe to run both queues concurrently.

## Evidence-grounded integration risks
1. **Operational closure risk (PC):** task gate is green but checkpoint commit failed (`pc-runtime/controller_state.json`), so backend progress can stall without semantic code changes.
2. **Regression risk (LP):** FE spec restoration could accidentally drop existing valid assertions (answer/abstention/citation/escaping/service isolation) while fixing structure.

## Directed next actions

### Package A
- **Implementation level:** 2
- **Owner:** PC
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:** task-06f accepted
- **allowed_paths:** `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`
- **Exact gate:**
  - `git diff --check`
  - `bash -lc "rm -rf target && ./scripts/task-gate.sh all && set -a && source ./.env && set +a && mvn -q -DskipTests spring-boot:run -Dspring-boot.run.main-class=com.riansares.r4r.ingestion.KnowledgeIngestionCli && rows=$(docker exec \"${POSTGRES_APP_CONTAINER:-r4r-postgres-app}\" psql -U \"${POSTGRES_APP_USER:-r4r}\" -d \"${POSTGRES_APP_DB:-r4r_rag}\" -Atqc 'SELECT count(*) FROM vector_store') && test \"$rows\" -gt 0"`
- **Acceptance evidence:** closure-complete run with controller-committable checkpoint state.

### Package B
- **Implementation level:** 1
- **Owner:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** task-fe-03c accepted
- **allowed_paths:** `frontend/**`, `docs/frontend/**` (effective single-file scope this pass)
- **Exact gate:**
  - `git diff --check`
  - `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
- **Acceptance evidence:** FE-03D gate green with valid DOM-state coverage and preserved existing tests.
