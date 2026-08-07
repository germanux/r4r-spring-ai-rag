# PC code review (Ring)

## Current evidence read
- `pc-runtime/gate_summary.md`: deterministic gate classification `green`, exit `0`.
- `pc-runtime/controller_state.json`: run status `CHECKPOINT_COMMIT_FAILED`, exit code `67`, error `Automatic gate-green checkpoint commit failed`.
- `pc-runtime/checkpoint.json`: `status: failed`, `head_after: null` despite gate-green timestamp.
- `pc-runtime/progress.json`: active task `task-07-populate-production-rag`, task status still `BLOCKED`.
- `worker-requests/PC.json`: reason `gate-green-checkpoint`, `codex_decision: null`, `checkpoint_head: null`.

## First current defect
Closure defect, not a new product defect: task-07 passed its exact deterministic gate, but controller checkpoint commit did not complete, so acceptance cannot close.

## Bounded next package
- **Implementation level:** Level 2
- **Assigned role:** PC
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:** `task-06f-ingestion-validation: ACCEPTED` (already satisfied)
- **allowed_paths (canonical):** `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`
- **Objective for one pass:** Produce closure-complete evidence for task-07 so controller can finish checkpoint/final commit.

### Exact action
1. Keep current task-07 scope only (no new architecture, no cross-queue edits).
2. Run `git diff --check` before expensive gate work.
3. Run the exact task gate once:
   - `bash -lc "rm -rf target && ./scripts/task-gate.sh all && set -a && source ./.env && set +a && mvn -q -DskipTests spring-boot:run -Dspring-boot.run.main-class=com.riansares.r4r.ingestion.KnowledgeIngestionCli && rows=$(docker exec \"${POSTGRES_APP_CONTAINER:-r4r-postgres-app}\" psql -U \"${POSTGRES_APP_USER:-r4r}\" -d \"${POSTGRES_APP_DB:-r4r_rag}\" -Atqc 'SELECT count(*) FROM vector_store') && test \"$rows\" -gt 0"`
4. Return evidence consistent across gate summary/checkpoint metadata/task status so controller can close.

### Acceptance gate
- `git diff --check`
- Exact task-07 gate command above
- Closure policy: `exact-gate-green + scope-clean + controller-commit`

### Avoid repeating
- Do **not** run unchanged loop iterations that only regenerate a gate-green result while leaving checkpoint commit unresolved.
- Do **not** wait for SURGICAL review (disabled).
