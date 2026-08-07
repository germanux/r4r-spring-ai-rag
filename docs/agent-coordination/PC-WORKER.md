# PC code review (backend)

## Evidence reviewed (RUN_DIR authoritative)

- `runtime/ring-agent/ring/20260807T014029Z/pc-runtime/controller_state.json`
- `runtime/ring-agent/ring/20260807T014029Z/pc-runtime/checkpoint.json`
- `runtime/ring-agent/ring/20260807T014029Z/pc-runtime/gate_summary.md`
- `runtime/ring-agent/ring/20260807T014029Z/pc-runtime/progress.json`
- `runtime/ring-agent/ring/20260807T014029Z/worker-request-manifest.json`
- `runtime/ring-agent/ring/20260807T014029Z/pc-git-status.txt`

## First current defect

The first current backend defect is **closure-evidence failure after a green gate**, not a newly demonstrated task-07 logic failure:

- `gate_summary.md` is green (`exit 0`).
- `checkpoint.json` has `gate_exit: 0` but `status: failed` and `head_after: null`.
- `controller_state.json` reports `CHECKPOINT_COMMIT_FAILED` (`exit_code: 67`).
- `worker-request-manifest.json` shows null `codex_decision`, `next_action`, and `checkpoint_head`.
- `progress.json` still marks `task-07-populate-production-rag` as `BLOCKED`.

## Directed work package

- **Implementation level:** Level 2
- **Assigned role:** PC
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:** `task-06f-ingestion-validation: ACCEPTED` (satisfied)
- **allowed_paths:** `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`

### One focused next action (single worker pass)

Run one closure-only pass for task-07:

1. `git diff --check`
2. Run the exact task-07 deterministic gate command.
3. Return explicit evidence of command exit and non-zero `vector_store` row count, with complete closure metadata for controller commit.

### Exact acceptance gates

- `git diff --check`
- `bash -lc "rm -rf target && ./scripts/task-gate.sh all && set -a && source ./.env && set +a && mvn -q -DskipTests spring-boot:run -Dspring-boot.run.main-class=com.riansares.r4r.ingestion.KnowledgeIngestionCli && rows=$(docker exec \"${POSTGRES_APP_CONTAINER:-r4r-postgres-app}\" psql -U \"${POSTGRES_APP_USER:-r4r}\" -d \"${POSTGRES_APP_DB:-r4r_rag}\" -Atqc 'SELECT count(*) FROM vector_store') && test \"$rows\" -gt 0"`
- Closure policy: `exact-gate-green + scope-clean + controller-commit`

## Avoid repeating

Do not resubmit another gate-green checkpoint request with null closure metadata fields; that has already produced a repeat `CHECKPOINT_COMMIT_FAILED` loop.
