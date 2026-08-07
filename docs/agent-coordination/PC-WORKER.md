# PC Code Review (Backend)

## Evidence reviewed (current RUN_DIR)

- `runtime/ring-agent/ring/20260807T014529Z/pc-runtime/progress.json`
- `runtime/ring-agent/ring/20260807T014529Z/pc-runtime/memory.md`
- `runtime/ring-agent/ring/20260807T014529Z/worker-request-manifest.json`
- `runtime/ring-agent/ring/20260807T014529Z/worker-requests/PC.json`
- `runtime/ring-agent/ring/20260807T014529Z/pc-git-status.txt`
- `runtime/ring-agent/ring/20260807T014529Z/pc-runtime/previous-ring-qwen3-directive.json`

## Current diagnosis

`task-07-populate-production-rag` has gate-green evidence (`gate_exit: 0`) and bounded backend changes in allowed scope, but closure evidence is incomplete in the worker request (`codex_decision: null`, `next_action: null`, `checkpoint_head: null`). Progress still marks task-07 as `BLOCKED`, so closure quality—not raw build/gate execution—is the first current defect.

## Directed next package

- **Implementation level:** Level 2
- **Assigned role:** PC
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:** `task-06f-ingestion-validation:ACCEPTED` (already satisfied per progress evidence)
- **allowed_paths (canonical):** `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`
- **Next action (single worker pass):** execute one closure-only backend pass, preserving scope, and emit complete closure metadata with explicit vector row-count proof.

## Exact gate and acceptance conditions

1. `git diff --check`
2. `bash -lc "rm -rf target && ./scripts/task-gate.sh all && set -a && source ./.env && set +a && mvn -q -DskipTests spring-boot:run -Dspring-boot.run.main-class=com.riansares.r4r.ingestion.KnowledgeIngestionCli && rows=$(docker exec \"${POSTGRES_APP_CONTAINER:-r4r-postgres-app}\" psql -U \"${POSTGRES_APP_USER:-r4r}\" -d \"${POSTGRES_APP_DB:-r4r_rag}\" -Atqc 'SELECT count(*) FROM vector_store') && test \"$rows\" -gt 0"`
3. Closure policy: exact-gate-green + scope-clean + controller-commit
4. Request metadata completeness: non-null `codex_decision`, `next_action`, `checkpoint_head` in request payload generated after the pass

## Avoid repeating

Do **not** submit another gate-green checkpoint request with null closure metadata fields.
