# PC code review — run 20260807T012027Z

## Current evidence reviewed

- `runtime/ring-agent/ring/20260807T012027Z/worker-requests/PC.json`
- `runtime/ring-agent/ring/20260807T012027Z/worker-request-manifest.json`
- `runtime/ring-agent/ring/20260807T012027Z/pc-runtime/progress.json`
- `runtime/ring-agent/ring/20260807T012027Z/pc-runtime/gate_summary.md`
- `runtime/ring-agent/ring/20260807T012027Z/pc-git-status.txt`

## Evidence-grounded diagnosis

The first current PC defect is **closure incompleteness**, not feature incompleteness:

- The worker request is `reason: gate-green-checkpoint` with `gate_exit: 0` for `task-07-populate-production-rag`.
- Changed paths are task-scoped backend files (`docs/backend/**`, `src/main/**`, `src/test/**`).
- `pc-runtime/progress.json` still marks task-07 as `BLOCKED` even though `last_gate_green_*` fields are present.

This indicates the queue is stuck in a gate-green handoff loop where closure-proof evidence is still insufficient for final controller closure.

## Bounded next action package

- **Implementation level:** Level 2
- **Assigned role:** PC
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:** `task-06f-ingestion-validation: ACCEPTED`
- **allowed_paths:** `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`

### One-pass action

Run one closure-focused pass only:

1. Keep current scoped edits.
2. Run `git diff --check` first.
3. Run the exact task gate once.
4. Return closure-complete deterministic evidence, including explicit non-zero `vector_store` count proof and command exits, so controller can checkpoint/final-commit without another ambiguity loop.

### Exact gate

1. `git diff --check`
2. `bash -lc "rm -rf target && ./scripts/task-gate.sh all && set -a && source ./.env && set +a && mvn -q -DskipTests spring-boot:run -Dspring-boot.run.main-class=com.riansares.r4r.ingestion.KnowledgeIngestionCli && rows=$(docker exec \"${POSTGRES_APP_CONTAINER:-r4r-postgres-app}\" psql -U \"${POSTGRES_APP_USER:-r4r}\" -d \"${POSTGRES_APP_DB:-r4r_rag}\" -Atqc 'SELECT count(*) FROM vector_store') && test \"$rows\" -gt 0"`
3. Closure policy: `exact-gate-green + scope-clean + controller-commit`

## Avoid repeating

Do not send another gate-green checkpoint request that lacks closure-complete diagnostics/metadata.

## Acceptance evidence expected from the worker

- Preflight whitespace check success.
- Exact task-07 gate success.
- Explicit captured proof that `vector_store` row count is `> 0`.
- Scoped diff remains within task `allowed_paths`.
