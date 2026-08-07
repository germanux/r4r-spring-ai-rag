# PC code review — run 20260807T011526Z

## Evidence reviewed

- `runtime/ring-agent/ring/20260807T011526Z/worker-requests/PC.json`
- `runtime/ring-agent/ring/20260807T011526Z/worker-request-manifest.json`
- `runtime/ring-agent/ring/20260807T011526Z/pc-runtime/progress.json`
- `runtime/ring-agent/ring/20260807T011526Z/pc-git-status.txt`
- `runtime/ring-agent/ring/20260807T011526Z/pc-runtime/previous-ring-qwen3-directive.json`

## Current diagnosis

`task-07-populate-production-rag` remains `BLOCKED` in progress despite a gate-green request (`gate_exit=0`) and scoped backend changes already present. The first current defect is **closure incompleteness** (handoff/evidence completeness for controller checkpoint/finalization), not a new architecture or scope gap.

## Bounded next action package

- **Implementation level:** Level 2  
- **Assigned role:** PC  
- **Task ID:** `task-07-populate-production-rag`  
- **Dependencies:** `task-06f-ingestion-validation:ACCEPTED` (already satisfied in progress evidence)  
- **allowed_paths (canonical write scope):** `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`

### One-pass objective

Perform one closure-focused pass from the current scoped backend diff:

1. `git diff --check`
2. Execute the exact task gate once:
   - `bash -lc "rm -rf target && ./scripts/task-gate.sh all && set -a && source ./.env && set +a && mvn -q -DskipTests spring-boot:run -Dspring-boot.run.main-class=com.riansares.r4r.ingestion.KnowledgeIngestionCli && rows=$(docker exec \"${POSTGRES_APP_CONTAINER:-r4r-postgres-app}\" psql -U \"${POSTGRES_APP_USER:-r4r}\" -d \"${POSTGRES_APP_DB:-r4r_rag}\" -Atqc 'SELECT count(*) FROM vector_store') && test \"$rows\" -gt 0"`
3. Return complete deterministic diagnostics for controller closure.

### Exact acceptance gate

- `git diff --check` is clean.
- Exact task command exits 0 and preserves non-zero `vector_store` proof.
- Closure policy satisfied: `exact-gate-green + scope-clean + controller-commit`.

## Avoid repeating

Do not run another unchanged “green but still blocked” loop that omits closure-complete diagnostics/metadata.
