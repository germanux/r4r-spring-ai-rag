# PC code review (Ring)

## Evidence reviewed

- `runtime/ring-agent/ring/20260807T002210Z/worker-requests/PC.json`
- `runtime/ring-agent/ring/20260807T002210Z/pc-runtime/progress.json`
- `runtime/ring-agent/ring/20260807T002210Z/pc-runtime/memory.md`
- `runtime/ring-agent/ring/20260807T002210Z/pc-runtime/manifest.json`
- `runtime/ring-agent/ring/20260807T002210Z/pc-git-status.txt`

## Current diagnosis

First current defect for PC is **closure-evidence incompleteness**, not a proven new backend implementation defect.

- Task is still active as `task-07-populate-production-rag` and marked `BLOCKED` in progress.
- Worker request shows a prior gate-green attempt (`gate_exit=0`) but `codex_decision=null` and `checkpoint_head=null`.
- Manifest shows no captured gate summary/review/checkpoint for this snapshot (`gate_summary`, `codex_review`, `checkpoint` are `null`).

Because SURGICAL is disabled in `.opencode/task-plan.hierarchy.json`, this task must not be blocked waiting for SURGICAL ACCEPT/REVISE.

## Bounded next package

- **Implementation level:** Level 2
- **Assigned role:** PC
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:**
  - Prior gate-green evidence exists (`worker-requests/PC.json`), but closure proof in this snapshot is incomplete.
  - No overlap with LP active file scope.
- **allowed_paths (canonical task scope):**
  - `pom.xml`
  - `src/main/**`
  - `src/test/**`
  - `docs/backend/**`
- **Focused action (single pass):**
  1. Keep changes bounded to task-07 scope only.
  2. Run whitespace guard first: `git diff --check`.
  3. Run exact task gate once and retain deterministic row-count/idempotency evidence.
  4. Stop after evidence is produced for controller closure.

## Exact gate

From `.opencode/task-plan.backend.json` for `task-07-populate-production-rag`:

`bash -lc "rm -rf target && ./scripts/task-gate.sh all && set -a && source ./.env && set +a && mvn -q -DskipTests spring-boot:run -Dspring-boot.run.main-class=com.riansares.r4r.ingestion.KnowledgeIngestionCli && rows=$(docker exec \"${POSTGRES_APP_CONTAINER:-r4r-postgres-app}\" psql -U \"${POSTGRES_APP_USER:-r4r}\" -d \"${POSTGRES_APP_DB:-r4r_rag}\" -Atqc 'SELECT count(*) FROM vector_store') && test \"$rows\" -gt 0"`

Closure requirement from hierarchy policy:

- `exact-gate-green + scope-clean + controller-commit`

## Avoid repeating

- Do **not** hold for SURGICAL review; SURGICAL is disabled.
- Do **not** run repeated unchanged heavy passes without adding closure-quality evidence.
