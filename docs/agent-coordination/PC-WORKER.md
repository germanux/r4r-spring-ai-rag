# PC code review (backend)

## Evidence reviewed

- `runtime/ring-agent/ring/20260807T013528Z/pc-runtime/controller_state.json`
- `runtime/ring-agent/ring/20260807T013528Z/pc-runtime/checkpoint.json`
- `runtime/ring-agent/ring/20260807T013528Z/pc-runtime/progress.json`
- `runtime/ring-agent/ring/20260807T013528Z/pc-runtime/gate_summary.md`
- `runtime/ring-agent/ring/20260807T013528Z/worker-request-manifest.json`
- `runtime/ring-agent/ring/20260807T013528Z/pc-git-status.txt`

## Current diagnosis

The first current defect is **closure failure after a green gate**, not a newly proven backend code failure:

- Gate summary is green (`exit 0`).
- A checkpoint was attempted for `task-07-populate-production-rag`.
- Controller state is `CHECKPOINT_COMMIT_FAILED` (`exit_code: 67`).
- Request metadata remains incomplete (`codex_decision: null`, `next_action: null`, `checkpoint_head: null`).
- Progress still marks task-07 as `BLOCKED`.

This means backend queue advancement is blocked by closure-grade evidence/metadata, not by direct proof that task-07 logic is currently failing.

## Directed next package

- **Implementation level:** Level 2
- **Assigned role:** PC
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:** `task-06f-ingestion-validation: ACCEPTED` (already satisfied)
- **allowed_paths:** `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`

### One-pass action

Run one closure-only pass to produce deterministic closure artifacts:

1. `git diff --check`
2. Exact task-07 gate command from `.opencode/task-plan.backend.json`
3. Return explicit evidence of non-zero `vector_store` row count and command exit status

### Exact acceptance gate

- `git diff --check`
- `bash -lc "rm -rf target && ./scripts/task-gate.sh all && set -a && source ./.env && set +a && mvn -q -DskipTests spring-boot:run -Dspring-boot.run.main-class=com.riansares.r4r.ingestion.KnowledgeIngestionCli && rows=$(docker exec \"${POSTGRES_APP_CONTAINER:-r4r-postgres-app}\" psql -U \"${POSTGRES_APP_USER:-r4r}\" -d \"${POSTGRES_APP_DB:-r4r_rag}\" -Atqc 'SELECT count(*) FROM vector_store') && test \"$rows\" -gt 0"`
- Closure policy: exact-gate-green + scope-clean + controller-commit

## Avoid repeating

Do not send another gate-green checkpoint request with null closure metadata and no closure-complete diagnostic packet; that repeats the same blocked loop.
