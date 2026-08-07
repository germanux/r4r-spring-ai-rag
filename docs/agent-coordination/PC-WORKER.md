# PC Code Review (Ring)

## Current evidence-backed defect

Task `task-07-populate-production-rag` is **not closable yet** even though the deterministic gate is green.

- `pc-runtime/gate_summary.md` shows `exit 0`.
- `pc-runtime/checkpoint.json` shows `status: failed` with `gate_exit: 0` and no `head_after`.
- `pc-runtime/controller_state.json` shows `status: CHECKPOINT_COMMIT_FAILED` and error `Automatic gate-green checkpoint commit failed`.
- `pc-runtime/progress.json` still marks task-07 as `BLOCKED`.

So the first current defect is **closure-quality failure**, not a failing test.

## Bounded next work package

- **Implementation level:** Level 2
- **Assigned role:** PC
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:** `task-06f-ingestion-validation:ACCEPTED`
- **allowed_paths (canonical):** `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`
- **Exact gate:**
  - `git diff --check`
  - `bash -lc "rm -rf target && ./scripts/task-gate.sh all && set -a && source ./.env && set +a && mvn -q -DskipTests spring-boot:run -Dspring-boot.run.main-class=com.riansares.r4r.ingestion.KnowledgeIngestionCli && rows=$(docker exec \"${POSTGRES_APP_CONTAINER:-r4r-postgres-app}\" psql -U \"${POSTGRES_APP_USER:-r4r}\" -d \"${POSTGRES_APP_DB:-r4r_rag}\" -Atqc 'SELECT count(*) FROM vector_store') && test \"$rows\" -gt 0"`

## Acceptance conditions for this pass

1. Deterministic task-07 gate exits `0`.
2. Returned evidence includes explicit non-null closure metadata (decision/next action/checkpoint head) and vector row-count proof.
3. Scope stays inside task-07 allowed paths.
4. Controller can complete closure policy: exact-gate-green + scope-clean + controller-commit.

## Avoid repeating

Do **not** submit another gate-green attempt that leaves checkpoint commit failed or closure metadata incomplete.
