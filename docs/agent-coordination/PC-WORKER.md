# PC code review (evidence-based)

## Current diagnosis

- Active task is `task-07-populate-production-rag` (`pc-runtime/progress.json`).
- Current request is `gate-green-checkpoint` with `gate_exit: 0`, but `checkpoint_head` is still `null` and task state remains non-accepted (`worker-requests/PC.json`, `pc-runtime/progress.json`).
- Working tree includes five backend/doc changes already staged in scope (`pc-git-status.txt`):
  - `docs/backend/production-ingestion-evidence.md`
  - `src/main/java/com/riansares/r4r/ingestion/KnowledgeIngestionService.java`
  - `src/main/java/com/riansares/r4r/vector/PgVectorKnowledgeStore.java`
  - `src/test/java/com/riansares/r4r/ingestion/KnowledgeIngestionServiceIT.java`
  - `src/test/java/com/riansares/r4r/ingestion/TestChildApplicationContextInitializer.java`

First current defect for PC is **closure incompleteness after gate-green evidence**, not missing implementation scope.

## Bounded next package

- **Implementation level:** Level 2
- **Assigned role:** PC
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:** `task-06f-ingestion-validation` already accepted (from `pc-runtime/progress.json` ledger)
- **allowed_paths (canonical):** `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**` (from `.opencode/task-plan.backend.json`)
- **Next action (single pass):** Run a closure-focused pass only: keep scope-clean backend/doc diff, run whitespace precheck, run exact gate once, and return closure-complete evidence.

## Exact gate

1. `git diff --check`
2. `bash -lc "rm -rf target && ./scripts/task-gate.sh all && set -a && source ./.env && set +a && mvn -q -DskipTests spring-boot:run -Dspring-boot.run.main-class=com.riansares.r4r.ingestion.KnowledgeIngestionCli && rows=$(docker exec \"${POSTGRES_APP_CONTAINER:-r4r-postgres-app}\" psql -U \"${POSTGRES_APP_USER:-r4r}\" -d \"${POSTGRES_APP_DB:-r4r_rag}\" -Atqc 'SELECT count(*) FROM vector_store') && test \"$rows\" -gt 0"`
3. Closure rule: `exact-gate-green + scope-clean + controller-commit`

## Acceptance evidence required

- Gate result demonstrating exit 0 for the exact task-07 command.
- Proof that changed paths remain inside the task allowed scope.
- Closure-ready diagnostic bundle (no missing checkpoint/commit-critical data).

## Avoid repeating

- Do **not** repeat unchanged “gate-green but no closure completion” loops.
