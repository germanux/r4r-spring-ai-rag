# PC code review (Ring cycle 20260807T015030Z)

## Evidence reviewed
- `runtime/ring-agent/ring/20260807T015030Z/worker-requests/PC.json`
- `runtime/ring-agent/ring/20260807T015030Z/pc-runtime/progress.json`
- `runtime/ring-agent/ring/20260807T015030Z/pc-runtime/previous-ring-qwen3-directive.json`
- `runtime/ring-agent/ring/20260807T015030Z/pc-git-status.txt`

## Current diagnosis
- Active backend task is `task-07-populate-production-rag`.
- The latest PC request is `reason: gate-green-checkpoint` with `gate_exit: 0`, but closure metadata fields are null (`codex_decision`, `next_action`, `checkpoint_head`).
- `progress.json` still marks task-07 as `BLOCKED`, so acceptance is not yet evidenced.

## First current defect (correction before new implementation)
Closure-quality evidence is incomplete, not a new feature gap. The immediate correction is to produce one deterministic closure pass with complete metadata and explicit ingestion proof.

## Bounded work package
- **Implementation level:** Level 2
- **Assigned role:** PC
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:** `task-06f-ingestion-validation:ACCEPTED` (already satisfied by progress evidence)
- **allowed_paths:** `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`
- **Next action (single pass):**
  1. Run `git diff --check`.
  2. Run the exact task gate once:
     - `bash -lc "rm -rf target && ./scripts/task-gate.sh all && set -a && source ./.env && set +a && mvn -q -DskipTests spring-boot:run -Dspring-boot.run.main-class=com.riansares.r4r.ingestion.KnowledgeIngestionCli && rows=$(docker exec \"${POSTGRES_APP_CONTAINER:-r4r-postgres-app}\" psql -U \"${POSTGRES_APP_USER:-r4r}\" -d \"${POSTGRES_APP_DB:-r4r_rag}\" -Atqc 'SELECT count(*) FROM vector_store') && test \"$rows\" -gt 0"`
  3. Return request metadata with non-null `codex_decision`, `next_action`, and `checkpoint_head` (or explicit null rationale if controller contract requires), plus explicit command exit and row-count evidence.

## Exact acceptance gate
- `git diff --check`
- Exact gate command above exits `0`
- Closure policy: `exact-gate-green + scope-clean + controller-commit`

## Avoid repeating
- Do **not** submit another gate-green checkpoint request with null closure metadata fields.

## Ring repository edits
- None. Ring made no product/test/config edits.
