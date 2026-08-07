# PC code review (current cycle)

## Evidence reviewed

- `runtime/ring-agent/ring/20260807T002711Z/worker-requests/PC.json`
- `runtime/ring-agent/ring/20260807T002711Z/pc-runtime/progress.json`
- `runtime/ring-agent/ring/20260807T002711Z/pc-runtime/gate_summary.md`
- `runtime/ring-agent/ring/20260807T002711Z/pc-git-status.txt`
- `runtime/ring-agent/ring/20260807T002711Z/pc-runtime/previous-ring-qwen3-directive.json`

## First current defect

Task `task-07-populate-production-rag` has a gate-green request (`gate_exit=0`) with scoped backend changes present, but closure proof is incomplete in the same evidence snapshot (`codex_decision=null`, `checkpoint_head=null`, progress still `BLOCKED`). The defect is **closure-evidence completeness**, not missing architecture.

## Bounded next action package

- **Implementation level:** Level 2
- **Assigned role:** PC
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:**
  - `task-06f-ingestion-validation: ACCEPTED` (already satisfied in `pc-runtime/progress.json`)
  - Existing task-07 bounded scope and deterministic gate contract
- **allowed_paths:**
  - `pom.xml`
  - `src/main/**`
  - `src/test/**`
  - `docs/backend/**`
- **Focused action (single pass):**
  1. Keep only one coherent closure pass inside current task scope.
  2. Run `git diff --check` before expensive gates.
  3. Run the exact deterministic task-07 command sequence once and retain evidence that `vector_store` row count is non-zero (and include idempotence proof artifacts expected by task intent).

## Exact acceptance gate

1. `git diff --check`
2. `bash -lc "rm -rf target && ./scripts/task-gate.sh all && set -a && source ./.env && set +a && mvn -q -DskipTests spring-boot:run -Dspring-boot.run.main-class=com.riansares.r4r.ingestion.KnowledgeIngestionCli && rows=$(docker exec \"${POSTGRES_APP_CONTAINER:-r4r-postgres-app}\" psql -U \"${POSTGRES_APP_USER:-r4r}\" -d \"${POSTGRES_APP_DB:-r4r_rag}\" -Atqc 'SELECT count(*) FROM vector_store') && test \"$rows\" -gt 0"`
3. Hierarchy closure policy: `exact-gate-green + scope-clean + controller-commit`

## Avoid repeating

- Do **not** hold for SURGICAL review/acceptance.
- Do **not** rerun unchanged loops without adding missing closure artifacts.
