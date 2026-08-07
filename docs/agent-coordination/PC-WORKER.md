# PC code review (backend)

## Evidence reviewed (current RUN_DIR)

- `runtime/ring-agent/ring/20260807T030125Z/pc-runtime/progress.json`
- `runtime/ring-agent/ring/20260807T030125Z/pc-runtime/controller_state.json`
- `runtime/ring-agent/ring/20260807T030125Z/pc-runtime/checkpoint.json`
- `runtime/ring-agent/ring/20260807T030125Z/pc-runtime/gate_summary.md`
- `runtime/ring-agent/ring/20260807T030125Z/pc-git-status.txt`
- `runtime/ring-agent/ring/20260807T030125Z/pc-runtime/previous-ring-qwen3-directive.json`

## First current defect

The first defect is **task closure failure after a green deterministic gate**.

- Gate evidence is green (`exit 0`) in `pc-runtime/gate_summary.md`.
- Task is still blocked in `pc-runtime/progress.json` (`task-07-populate-production-rag` status `BLOCKED`).
- Controller reports `CHECKPOINT_COMMIT_FAILED` (`exit_code 67`) in `pc-runtime/controller_state.json`.
- Checkpoint metadata confirms closure failure: `status: failed`, `head_after: null` in `pc-runtime/checkpoint.json`.

This is not a new product-failure diagnosis; it is closure-evidence failure.

## Directed bounded package

- **Implementation level:** Level 2 (PC)
- **Assigned role:** PC
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:** `task-06f-ingestion-validation:ACCEPTED` (already satisfied)
- **allowed_paths (canonical):**
  - `pom.xml`
  - `src/main/**`
  - `src/test/**`
  - `docs/backend/**`
- **Exact gate(s):**
  1. `git diff --check`
  2. `bash -lc "rm -rf target && ./scripts/task-gate.sh all && set -a && source ./.env && set +a && mvn -q -DskipTests spring-boot:run -Dspring-boot.run.main-class=com.riansares.r4r.ingestion.KnowledgeIngestionCli && rows=$(docker exec \"${POSTGRES_APP_CONTAINER:-r4r-postgres-app}\" psql -U \"${POSTGRES_APP_USER:-r4r}\" -d \"${POSTGRES_APP_DB:-r4r_rag}\" -Atqc 'SELECT count(*) FROM vector_store') && test \"$rows\" -gt 0"`

## One-pass next action

Run exactly one **closure-only** retry pass to produce all required closure artifacts:

1. scope-clean preflight (`git diff --check`),
2. exact task-07 gate,
3. successful controller/checkpoint closure metadata (no `CHECKPOINT_COMMIT_FAILED`, non-null commit/head metadata).

## Acceptance conditions

Accept only when all are present in the same attempt:

1. exact gate green,
2. scope-clean,
3. controller-commit closure evidence.

If this authorized retry fails again on closure metadata, move task to `HOLD` for operator diagnosis (no second recovery authorization for the same blocked state).

## Avoid repeating

Do **not** submit another gate-green backend run that still ends with failed checkpoint metadata (`head_after: null` / controller commit failure).
