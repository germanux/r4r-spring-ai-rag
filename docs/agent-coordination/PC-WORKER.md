# PC code review (run 20260807T021032Z)

## Evidence reviewed

- `runtime/ring-agent/ring/20260807T021032Z/worker-requests/PC.json`
- `runtime/ring-agent/ring/20260807T021032Z/worker-request-manifest.json`
- `runtime/ring-agent/ring/20260807T021032Z/pc-runtime/progress.json`
- `runtime/ring-agent/ring/20260807T021032Z/pc-runtime/memory.md`
- `runtime/ring-agent/ring/20260807T021032Z/pc-git-status.txt`
- `runtime/ring-agent/ring/20260807T021032Z/pc-runtime/previous-ring-qwen3-directive.json`

## First current defect

Closure-quality evidence is incomplete for the active backend task:

- `worker-requests/PC.json` reports `gate_exit: 0` for `task-07-populate-production-rag`.
- The same request still has `codex_decision: null`, `next_action: null`, `checkpoint_head: null`.
- `pc-runtime/progress.json` still marks task-07 as `BLOCKED`.

This is not a proven fresh gate failure; it is a deterministic closure-evidence defect.

## Bounded next package

- **Implementation level:** Level 2
- **Assigned role:** PC
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:**
  - `task-06f-ingestion-validation: ACCEPTED`
  - no cross-queue dependencies this cycle
- **allowed_paths:**
  - `pom.xml`
  - `src/main/**`
  - `src/test/**`
  - `docs/backend/**`

### One focused next action (single pass)

Run one closure-quality pass for task-07 only: keep scope unchanged, run `git diff --check`, execute the exact task-07 gate once, and return non-null closure metadata with explicit `vector_store` row-count proof.

### Exact deterministic gate

1. `git diff --check`
2. `bash -lc "rm -rf target && ./scripts/task-gate.sh all && set -a && source ./.env && set +a && mvn -q -DskipTests spring-boot:run -Dspring-boot.run.main-class=com.riansares.r4r.ingestion.KnowledgeIngestionCli && rows=$(docker exec \"${POSTGRES_APP_CONTAINER:-r4r-postgres-app}\" psql -U \"${POSTGRES_APP_USER:-r4r}\" -d \"${POSTGRES_APP_DB:-r4r_rag}\" -Atqc 'SELECT count(*) FROM vector_store') && test \"$rows\" -gt 0"`
3. Closure policy: `exact-gate-green + scope-clean + controller-commit`

## Acceptance evidence required

- Non-null `codex_decision`, `next_action`, and `checkpoint_head` in closure/request payload.
- Gate exit `0` for the exact task-07 command.
- Explicit recorded value proving `vector_store` row count is `> 0`.
- Diff remains inside `allowed_paths` only.

## Avoid repeating

Do not submit another gate-green checkpoint request with null closure metadata fields.
