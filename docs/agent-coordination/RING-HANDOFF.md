# Backend ↔ Frontend handoff

## Queue status split

### Backend (PC)
- **Task:** `task-07-populate-production-rag`
- **State:** actionable now (`CONTINUE`)
- **Defect type:** closure-quality evidence gap after green deterministic gate
- **Implementation level / owner:** Level 2 / PC

### Frontend (LP)
- **Task:** `task-fe-03d-dom-state-tests`
- **State:** temporarily blocked (`HOLD`) due controller global attempt-limit stop
- **Defect type:** worker execution budget stop + unresolved one-file correction packet
- **Implementation level / owner:** Level 1 / LP

## Scope overlap check

- PC allowed_paths: `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`
- LP allowed_paths: `frontend/**`, `docs/frontend/**` (narrowed this pass to one spec file)
- **Result:** No backend/frontend write-scope overlap; safe to continue backend while frontend is held.

## Bounded packages

1. **Package PC-07-CLOSURE (Level 2, PC)**
   - **Task ID:** `task-07-populate-production-rag`
   - **Dependencies:** task-06f accepted
   - **allowed_paths:** `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`
   - **Exact gate:**
     - `git diff --check`
     - `bash -lc "rm -rf target && ./scripts/task-gate.sh all && set -a && source ./.env && set +a && mvn -q -DskipTests spring-boot:run -Dspring-boot.run.main-class=com.riansares.r4r.ingestion.KnowledgeIngestionCli && rows=$(docker exec \"${POSTGRES_APP_CONTAINER:-r4r-postgres-app}\" psql -U \"${POSTGRES_APP_USER:-r4r}\" -d \"${POSTGRES_APP_DB:-r4r_rag}\" -Atqc 'SELECT count(*) FROM vector_store') && test \"$rows\" -gt 0"`
   - **Acceptance evidence:** controller/checkpoint success metadata + row-count proof + scope-clean diff.

2. **Package LP-03D-REPAIR (Level 1, LP, pending unblock)**
   - **Task ID:** `task-fe-03d-dom-state-tests`
   - **Dependencies:** task-fe-03c accepted; controller attempt budget reset/rearm
   - **allowed_paths (narrowed):** `frontend/src/app/features/rag/rag-page.component.spec.ts`
   - **Exact gate:**
     - `git diff --check`
     - `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
   - **Acceptance evidence:** one-file scoped patch, green gate, and controller completion.

## Integration risks to watch

- If PC repeats gate-green without resolving checkpoint commit metadata, backend queue can deadlock on closure.
- LP repeated spec rewrites can regress existing DOM assertions; enforce one-file correction packet exactly.
