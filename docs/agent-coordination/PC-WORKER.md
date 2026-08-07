# PC code review (evidence-grounded)

## Current diagnosis

- Active task in progress is `task-07-populate-production-rag`.
- `pc-runtime/gate_summary.md` shows deterministic gate classification `green` and exit `0`.
- `pc-runtime/controller_state.json` reports `CHECKPOINT_COMMIT_FAILED` with exit code `67`.
- `pc-runtime/checkpoint.json` reports `status: failed` and `head_after: null`.
- `pc-runtime/progress.json` still marks task-07 as `BLOCKED`.

This is a **closure-quality defect**, not a first-failure test defect: task acceptance evidence is incomplete despite a green gate.

## Directed next package

- **Implementation level:** Level 2
- **Assigned role:** PC
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:** `task-06f-ingestion-validation` accepted (already satisfied)
- **allowed_paths (canonical):** `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`
- **One-pass objective:** Produce closure-ready evidence (gate green + scope clean + controller commit metadata) without widening scope.

## Exact gate and acceptance conditions

1. `git diff --check`
2. `bash -lc "rm -rf target && ./scripts/task-gate.sh all && set -a && source ./.env && set +a && mvn -q -DskipTests spring-boot:run -Dspring-boot.run.main-class=com.riansares.r4r.ingestion.KnowledgeIngestionCli && rows=$(docker exec \"${POSTGRES_APP_CONTAINER:-r4r-postgres-app}\" psql -U \"${POSTGRES_APP_USER:-r4r}\" -d \"${POSTGRES_APP_DB:-r4r_rag}\" -Atqc 'SELECT count(*) FROM vector_store') && test \"$rows\" -gt 0"`
3. Closure policy evidence present: `exact-gate-green + scope-clean + controller-commit`

### Required evidence artifacts for acceptance

- Updated controller state showing successful completion for this pass.
- Checkpoint metadata with non-null commit/head outcomes.
- Row-count proof kept in task evidence (`rows > 0`).

## Avoid repeating

- Do **not** submit another pass that only proves gate green while leaving checkpoint/controller closure metadata failed or null.

## Evidence consulted

- `runtime/ring-agent/ring/20260807T023359Z/pc-runtime/controller_state.json`
- `runtime/ring-agent/ring/20260807T023359Z/pc-runtime/checkpoint.json`
- `runtime/ring-agent/ring/20260807T023359Z/pc-runtime/gate_summary.md`
- `runtime/ring-agent/ring/20260807T023359Z/pc-runtime/progress.json`
- `runtime/ring-agent/ring/20260807T023359Z/pc-git-status.txt`
