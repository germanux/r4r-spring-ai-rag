# PC code review (evidence-based)

## Current diagnosis

- Active task: `task-07-populate-production-rag` (`pc-runtime/progress.json`: status still `BLOCKED`).
- Deterministic gate summary is green (`pc-runtime/gate_summary.md`, exit `0`).
- Latest worker request is incomplete for closure (`worker-requests/PC.json`):
  - `codex_decision: null`
  - `next_action: null`
  - `checkpoint_head: null`
- PC worktree snapshot shows bounded backend/doc changes in allowed scope (`pc-git-status.txt`).

## First current defect

Closure evidence is incomplete/inconsistent, not a demonstrated code failure: gate success exists, but acceptance-ready metadata is missing and task status remains blocked.

## Bounded next action package

- **Implementation level:** Level 2
- **Assigned role:** PC
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:** `task-06f-ingestion-validation:ACCEPTED`
- **allowed_paths (canonical):** `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`
- **Exact gate:**
  1. `git diff --check`
  2. `bash -lc "rm -rf target && ./scripts/task-gate.sh all && set -a && source ./.env && set +a && mvn -q -DskipTests spring-boot:run -Dspring-boot.run.main-class=com.riansares.r4r.ingestion.KnowledgeIngestionCli && rows=$(docker exec \"${POSTGRES_APP_CONTAINER:-r4r-postgres-app}\" psql -U \"${POSTGRES_APP_USER:-r4r}\" -d \"${POSTGRES_APP_DB:-r4r_rag}\" -Atqc 'SELECT count(*) FROM vector_store') && test \"$rows\" -gt 0"`

## Acceptance evidence required

1. Gate exit `0` for the exact task-07 gate command above.
2. Explicit non-zero `vector_store` row-count proof captured in the run evidence.
3. Non-null closure metadata in the PC request artifact (`codex_decision`, `next_action`, `checkpoint_head`).
4. Scope-clean diff inside canonical `allowed_paths`.

## Avoid repeating

Do not submit another gate-green checkpoint request with null closure metadata fields.
