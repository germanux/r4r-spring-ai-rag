# Backend ↔ Frontend handoff (cycle 20260807T015030Z)

## Queue status snapshot
- **Backend (PC):** `task-07-populate-production-rag` active, gate-green evidence present, closure metadata incomplete.
- **Frontend (LP):** `task-fe-03d-dom-state-tests` active, gate currently failing with Codex REVISE correction packet.

## Scope separation check
- PC working scope: backend paths (`pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`).
- LP working scope: frontend test file (`frontend/src/app/features/rag/rag-page.component.spec.ts`) within frontend scope.
- **Overlap:** none. Parallel execution is safe.

## Cross-stack dependencies
- No immediate blocking dependency between current PC and LP corrections.
- LP FE-03D completion affects later frontend packages (`task-fe-03e`, `task-fe-03f`, `task-fe-04`) but does not depend on PC task-07 completion in this cycle.
- PC task-07 completion is prerequisite for later backend semantic and production smoke tasks (`task-08`, `task-09`).

## Directed next actions

### Package A
- **Implementation level:** Level 2
- **Assigned role:** PC
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:** `task-06f-ingestion-validation:ACCEPTED`
- **allowed_paths:** `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`
- **Exact gate:**
  - `git diff --check`
  - `bash -lc "rm -rf target && ./scripts/task-gate.sh all && set -a && source ./.env && set +a && mvn -q -DskipTests spring-boot:run -Dspring-boot.run.main-class=com.riansares.r4r.ingestion.KnowledgeIngestionCli && rows=$(docker exec \"${POSTGRES_APP_CONTAINER:-r4r-postgres-app}\" psql -U \"${POSTGRES_APP_USER:-r4r}\" -d \"${POSTGRES_APP_DB:-r4r_rag}\" -Atqc 'SELECT count(*) FROM vector_store') && test \"$rows\" -gt 0"`
- **Acceptance evidence required:** exit 0 + non-zero row count + non-null closure metadata fields.

### Package B
- **Implementation level:** Level 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** `task-fe-03c-citations:ACCEPTED`
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate:**
  - `git diff --check`
  - `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
- **Acceptance evidence required:** gate exit 0 with Codex-prescribed DOM assertions and consistent diagnostics.

## Integration risks to watch next cycle
1. PC may continue to produce green gate output without closure metadata completion; treat as non-accepted until metadata is complete.
2. LP may partially apply test fixes and leave hidden structural/whitespace defects; `git diff --check` must gate before running FE task gate.
