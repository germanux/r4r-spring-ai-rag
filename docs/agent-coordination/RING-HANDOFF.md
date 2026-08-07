# Backend ↔ Frontend handoff

## Queue snapshot

- **Backend / PC:** `task-07-populate-production-rag` has a green gate but remains blocked by closure commit failure (`CHECKPOINT_COMMIT_FAILED`).
- **Frontend / LP:** `task-fe-03d-dom-state-tests` remains red with an active single-file Codex correction packet.

## Concurrency and write-scope safety

Next packages are disjoint and safe to run in parallel:

- **PC allowed_paths:** `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`
- **LP allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts`

No backend/frontend path overlap is present.

## Bounded packages for next pass

### PC package

- **Level:** 2
- **Role:** PC
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:** `task-06f-ingestion-validation: ACCEPTED`
- **allowed_paths:** `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`
- **Exact gate:**
  - `git diff --check`
  - `bash -lc "rm -rf target && ./scripts/task-gate.sh all && set -a && source ./.env && set +a && mvn -q -DskipTests spring-boot:run -Dspring-boot.run.main-class=com.riansares.r4r.ingestion.KnowledgeIngestionCli && rows=$(docker exec \"${POSTGRES_APP_CONTAINER:-r4r-postgres-app}\" psql -U \"${POSTGRES_APP_USER:-r4r}\" -d \"${POSTGRES_APP_DB:-r4r_rag}\" -Atqc 'SELECT count(*) FROM vector_store') && test \"$rows\" -gt 0"`
  - Closure policy `exact-gate-green + scope-clean + controller-commit`

### LP package

- **Level:** 1
- **Role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** `task-fe-03c-citations: ACCEPTED`
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate:**
  - `git diff --check`
  - `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
  - Closure policy `exact-gate-green + scope-clean + controller-commit`

## Integration risks to monitor

1. Backend queue can stall despite green gates if closure metadata stays incomplete.
2. Frontend queue can stall if LP repeats patterns already rejected by Codex instead of applying the bounded correction packet exactly.
