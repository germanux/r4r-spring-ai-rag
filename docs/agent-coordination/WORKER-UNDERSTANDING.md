# Worker understanding checks

## PC (task-07-populate-production-rag)

What PC must demonstrate in the next pass:

1. Understand that the current defect is **not** new ingestion architecture; it is incomplete closure evidence for an already gate-green attempt.
2. Keep work within Level-2 bounded scope only:
   - `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`.
3. Execute the deterministic closure sequence once, preserving artifacts that prove:
   - gate success,
   - non-zero `vector_store` row count,
   - idempotent behavior evidence expected by task-07 intent.

**Exact gate:**
- `git diff --check`
- `bash -lc "rm -rf target && ./scripts/task-gate.sh all && set -a && source ./.env && set +a && mvn -q -DskipTests spring-boot:run -Dspring-boot.run.main-class=com.riansares.r4r.ingestion.KnowledgeIngestionCli && rows=$(docker exec \"${POSTGRES_APP_CONTAINER:-r4r-postgres-app}\" psql -U \"${POSTGRES_APP_USER:-r4r}\" -d \"${POSTGRES_APP_DB:-r4r_rag}\" -Atqc 'SELECT count(*) FROM vector_store') && test \"$rows\" -gt 0"`
- closure: `exact-gate-green + scope-clean + controller-commit`

## LP (task-fe-03d-dom-state-tests)

What LP must demonstrate in the next pass:

1. Apply Codex REVISE literally in one small Level-1 patch in only:
   - `frontend/src/app/features/rag/rag-page.component.spec.ts`.
2. Restore valid structure before adding assertions.
3. Implement only the prescribed three tests (pending-loading/duplicate-submit, success-reset, transport-error-reset).
4. Preserve existing valid FE-03D coverage and avoid all rejected anti-patterns.

**Exact gate:**
- `git diff --check`
- `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
- closure: `exact-gate-green + scope-clean + controller-commit`

## Shared do-not-repeat guidance

- No SURGICAL dispatch or waiting for SURGICAL ACCEPT/REVISE.
- No scope widening.
- No unchanged retry loops without new evidence.
