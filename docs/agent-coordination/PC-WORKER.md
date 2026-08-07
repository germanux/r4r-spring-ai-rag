# PC code review (backend)

## Evidence reviewed

- `runtime/ring-agent/ring/20260807T011026Z/worker-requests/PC.json`
- `runtime/ring-agent/ring/20260807T011026Z/pc-runtime/progress.json`
- `runtime/ring-agent/ring/20260807T011026Z/pc-runtime/gate_summary.md`
- `runtime/ring-agent/ring/20260807T011026Z/pc-git-status.txt`
- `runtime/ring-agent/ring/20260807T011026Z/pc-runtime/previous-ring-qwen3-directive.json`

## Current diagnosis

Task `task-07-populate-production-rag` has direct gate-green evidence (`gate_exit=0`) but is still `BLOCKED` in progress state and has `checkpoint_head: null` in the worker request. The first current defect is **closure incompleteness** (checkpoint/commit handoff readiness), not missing feature implementation.

## Bounded next work package

- **Implementation level:** Level 2
- **Assigned role:** PC
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:** `task-06f-ingestion-validation` accepted (already satisfied); no new cross-queue dependency added
- **allowed_paths (canonical write scope):**
  - `pom.xml`
  - `src/main/**`
  - `src/test/**`
  - `docs/backend/**`
- **One-pass objective:** produce closure-complete deterministic evidence for the already-green task-07 path.

## Exact gate and acceptance conditions

1. `git diff --check`
2. `bash -lc "rm -rf target && ./scripts/task-gate.sh all && set -a && source ./.env && set +a && mvn -q -DskipTests spring-boot:run -Dspring-boot.run.main-class=com.riansares.r4r.ingestion.KnowledgeIngestionCli && rows=$(docker exec \"${POSTGRES_APP_CONTAINER:-r4r-postgres-app}\" psql -U \"${POSTGRES_APP_USER:-r4r}\" -d \"${POSTGRES_APP_DB:-r4r_rag}\" -Atqc 'SELECT count(*) FROM vector_store') && test \"$rows\" -gt 0"`
3. Closure policy check: exact gate green + scope clean + controller-owned checkpoint/commit

## Avoid repeating

Do not repeat an unchanged “gate-green but still uncloseable” loop. The next pass must return complete, coherent diagnostics and metadata supporting immediate controller closure.
