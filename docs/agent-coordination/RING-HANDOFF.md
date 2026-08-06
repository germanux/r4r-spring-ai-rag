# Backend ↔ Frontend handoff (cycle 20260806T184128Z)

## Queue decisions
- **Backend lane (PC task context): REVIEW** on `task-07-populate-production-rag` via SURGICAL review-only disposition.
- **Frontend lane (LP task context): CONTINUE** on `task-fe-03d-dom-state-tests` with one bounded Level-1 revise pass.

## Ownership and scope separation
- **Backend review package (Level 3, SURGICAL):**
  - task ID: `task-07-populate-production-rag`
  - dependency context: `BE-07-B` depends on `BE-07-A:ACCEPTED`
  - allowed_paths for any resumed PC implementation: `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`
  - current pass objective is review/disposition; no Ring product edits.
- **Frontend correction package (Level 1, LP):**
  - task ID: `task-fe-03d-dom-state-tests` (`FE-03D-A`)
  - allowed_paths: `frontend/src/app/features/rag/rag-page.component.spec.ts`

These active directives are disjoint (backend vs one frontend spec file), so concurrent progression is safe.

## Sequencing controls
1. PC cannot be closed on gate status alone; current packet still lacks SURGICAL Codex disposition and has checkpoint-commit failure evidence.
2. LP must convert REVISE instructions into a concrete non-empty scoped patch and produce consistent gate evidence.
3. Both lanes require SURGICAL `ACCEPT` before closure under `.opencode/task-plan.hierarchy.json` review policy.

## Exact gates to preserve
- Backend (`task-07-populate-production-rag`):
  - `bash -lc "rm -rf target && ./scripts/task-gate.sh all && set -a && source ./.env && set +a && mvn -q -DskipTests spring-boot:run -Dspring-boot.run.main-class=com.riansares.r4r.ingestion.KnowledgeIngestionCli && rows=$(docker exec \"${POSTGRES_APP_CONTAINER:-r4r-postgres-app}\" psql -U \"${POSTGRES_APP_USER:-r4r}\" -d \"${POSTGRES_APP_DB:-r4r_rag}\" -Atqc 'SELECT count(*) FROM vector_store') && test \"$rows\" -gt 0"`
- Frontend (`task-fe-03d-dom-state-tests`):
  - `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
  - pre-gate: `git diff --check`

## Integration risks to monitor next cycle
- PC packet inconsistency (checkpoint gate green vs gate summary red) can cause incorrect release decisions unless surgically reconciled.
- LP may continue REVISE churn if selector/assertion mapping is not explicitly reflected in both tests and understanding evidence.
