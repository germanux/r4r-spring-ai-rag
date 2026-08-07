# PC code review (current cycle)

## Evidence reviewed

- `runtime/ring-agent/ring/20260807T020532Z/worker-requests/PC.json`
- `runtime/ring-agent/ring/20260807T020532Z/worker-request-manifest.json`
- `runtime/ring-agent/ring/20260807T020532Z/pc-runtime/progress.json`
- `runtime/ring-agent/ring/20260807T020532Z/pc-runtime/gate_summary.md`
- `runtime/ring-agent/ring/20260807T020532Z/pc-git-status.txt`

## Current diagnosis

The first current PC defect is **closure-quality, not a proven red gate**:

- The worker request is `reason: gate-green-checkpoint` with `gate_exit: 0`.
- The same request still has `codex_decision: null`, `next_action: null`, `checkpoint_head: null`.
- `pc-runtime/progress.json` keeps `task-07-populate-production-rag` in `BLOCKED` despite `last_gate_green_*` fields being present.

This indicates incomplete closure evidence packaging for the active task, which prevents deterministic controller closure.

## Bounded next package

- **Implementation level:** Level 2
- **Assigned role:** PC
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:**
  - `task-06f-ingestion-validation: ACCEPTED` (already evidenced in `pc-runtime/progress.json`)
  - existing task scope only; no cross-queue changes
- **allowed_paths:**
  - `pom.xml`
  - `src/main/**`
  - `src/test/**`
  - `docs/backend/**`

### Exact next action for one pass

Run one closure-quality pass for task-07 only: keep scope unchanged, run whitespace guard, execute the exact task gate once, and return non-null closure metadata together with explicit `vector_store` row-count proof.

### Exact gate

1. `git diff --check`
2. `bash -lc "rm -rf target && ./scripts/task-gate.sh all && set -a && source ./.env && set +a && mvn -q -DskipTests spring-boot:run -Dspring-boot.run.main-class=com.riansares.r4r.ingestion.KnowledgeIngestionCli && rows=$(docker exec \"${POSTGRES_APP_CONTAINER:-r4r-postgres-app}\" psql -U \"${POSTGRES_APP_USER:-r4r}\" -d \"${POSTGRES_APP_DB:-r4r_rag}\" -Atqc 'SELECT count(*) FROM vector_store') && test \"$rows\" -gt 0"`
3. Closure policy: `exact-gate-green + scope-clean + controller-commit`

## Acceptance evidence required

- Non-null `codex_decision`, `next_action`, and `checkpoint_head` in the resulting closure packet/request.
- Deterministic gate exit `0` for task-07 exact command.
- Explicit recorded row count proving `vector_store > 0`.
- Diff remains within PC allowed_paths.

## Avoid repeating

Do not submit another gate-green checkpoint request with null closure metadata fields.
