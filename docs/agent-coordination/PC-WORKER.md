# PC code review (Ring)

## Evidence reviewed (current RUN_DIR)

- `runtime/ring-agent/ring/20260807T005523Z/worker-requests/PC.json`
- `runtime/ring-agent/ring/20260807T005523Z/worker-request-manifest.json`
- `runtime/ring-agent/ring/20260807T005523Z/pc-runtime/progress.json`
- `runtime/ring-agent/ring/20260807T005523Z/pc-runtime/memory.md`
- `runtime/ring-agent/ring/20260807T005523Z/pc-runtime/previous-ring-qwen3-directive.json`
- `runtime/ring-agent/ring/20260807T005523Z/pc-git-status.txt`

## Current diagnosis

1. Task in scope is still `task-07-populate-production-rag`.
2. The worker has already produced a gate-green checkpoint request (`gate_exit=0`) for run `20260807T005415Z`.
3. Closure is not yet evidenced as complete in current snapshot: request fields `codex_decision` and `checkpoint_head` remain `null`, and `pc-runtime/progress.json` still marks task-07 as `BLOCKED`.

## First current defect to correct

**Defect class:** closure-evidence incompleteness (not a proven new code failure in this RUN_DIR).

The backend pass appears to have executed successfully once, but controller-closeable proof is incomplete/inconsistent in current evidence state.

## Bounded next package

- **Implementation level:** Level 2
- **Assigned role:** PC
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:** `task-06f-ingestion-validation:ACCEPTED` (already satisfied per progress)
- **allowed_paths:** `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`
- **Exact gate:**
  1. `git diff --check`
  2. `bash -lc "rm -rf target && ./scripts/task-gate.sh all && set -a && source ./.env && set +a && mvn -q -DskipTests spring-boot:run -Dspring-boot.run.main-class=com.riansares.r4r.ingestion.KnowledgeIngestionCli && rows=$(docker exec \"${POSTGRES_APP_CONTAINER:-r4r-postgres-app}\" psql -U \"${POSTGRES_APP_USER:-r4r}\" -d \"${POSTGRES_APP_DB:-r4r_rag}\" -Atqc 'SELECT count(*) FROM vector_store') && test \"$rows\" -gt 0"`

## Acceptance evidence required

- Deterministic gate exits green for the exact task-07 command.
- Evidence bundle is closure-complete (consistent gate result + non-zero row proof + idempotent ingestion proof in task-owned evidence/doc updates).
- Scope remains clean and controller can close with policy: exact-gate-green + scope-clean + controller-commit.

## Avoid repeating

- Do **not** block on SURGICAL.
- Do **not** rerun unchanged full cycles without adding missing closure-complete evidence.
