# Backend ↔ Frontend handoff

## Queue status snapshot

- **Backend (PC):** active `task-07-populate-production-rag`; prior gate-green request exists, but closure artifacts are incomplete in this RUN_DIR snapshot.
- **Frontend (LP):** active `task-fe-03d-dom-state-tests`; current gate is red (`exit=2`) with Codex REVISE instructions.

## Disjoint ownership and write scopes

No overlap is required for the next pass:

- **PC allowed_paths:** `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`
- **LP allowed_paths:** `frontend/**`, `docs/frontend/**`

This supports concurrent progress without integration contention.

## Immediate packages

### Package A
- **Level:** 2
- **Role:** PC
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:** existing gate-green evidence from prior attempt; closure evidence incomplete.
- **allowed_paths:** `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`
- **Exact gate:**
  - `git diff --check`
  - `bash -lc "rm -rf target && ./scripts/task-gate.sh all && set -a && source ./.env && set +a && mvn -q -DskipTests spring-boot:run -Dspring-boot.run.main-class=com.riansares.r4r.ingestion.KnowledgeIngestionCli && rows=$(docker exec \"${POSTGRES_APP_CONTAINER:-r4r-postgres-app}\" psql -U \"${POSTGRES_APP_USER:-r4r}\" -d \"${POSTGRES_APP_DB:-r4r_rag}\" -Atqc 'SELECT count(*) FROM vector_store') && test \"$rows\" -gt 0"`
- **Acceptance evidence:** deterministic gate-green output and scope-clean diff suitable for controller commit.

### Package B
- **Level:** 1
- **Role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** Codex REVISE packet currently available; no backend dependency.
- **allowed_paths:** `frontend/**`, `docs/frontend/**` (this pass should edit only `frontend/src/app/features/rag/rag-page.component.spec.ts`).
- **Exact gate:**
  - `git diff --check`
  - `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
- **Acceptance evidence:** passing FE-03D gate, scoped patch, and consistent diagnostics/understanding report.

## Integration risks to watch

1. Backend proof depends on container/database runtime state; false negatives are possible when environment drift occurs.
2. Frontend spec surgery can accidentally remove previously accepted assertions if cleanup and additions are not tightly bounded.
