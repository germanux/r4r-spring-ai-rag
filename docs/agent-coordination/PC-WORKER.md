# PC code review — run 20260807T005023Z

## Evidence reviewed

- `runtime/ring-agent/ring/20260807T005023Z/worker-requests/PC.json`
- `runtime/ring-agent/ring/20260807T005023Z/pc-runtime/progress.json`
- `runtime/ring-agent/ring/20260807T005023Z/pc-runtime/memory.md`
- `runtime/ring-agent/ring/20260807T005023Z/pc-runtime/previous-ring-qwen3-directive.json`
- `runtime/ring-agent/ring/20260807T005023Z/pc-git-status.txt`

## Current diagnosis (first defect)

`task-07-populate-production-rag` already has a gate-green worker request (`gate_exit=0`), but closure artifacts are incomplete in this snapshot (`codex_decision=null`, `checkpoint_head=null`) and `progress.json` still marks task-07 as `BLOCKED`.

This means the first current defect is **closure-proof completeness**, not new backend feature implementation.

## Bounded next package

- **Implementation level:** Level 2
- **Assigned role:** PC
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:** `task-06f-ingestion-validation` accepted (already true per progress)
- **allowed_paths:** `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**` (from backend plan/directive)
- **Exact gate:**
  1. `git diff --check`
  2. `bash -lc "rm -rf target && ./scripts/task-gate.sh all && set -a && source ./.env && set +a && mvn -q -DskipTests spring-boot:run -Dspring-boot.run.main-class=com.riansares.r4r.ingestion.KnowledgeIngestionCli && rows=$(docker exec \"${POSTGRES_APP_CONTAINER:-r4r-postgres-app}\" psql -U \"${POSTGRES_APP_USER:-r4r}\" -d \"${POSTGRES_APP_DB:-r4r_rag}\" -Atqc 'SELECT count(*) FROM vector_store') && test \"$rows\" -gt 0"`
  3. Closure policy: exact-gate-green + scope-clean + controller commit

## Acceptance evidence required

1. Deterministic gate exits 0.
2. Evidence clearly retains non-zero `vector_store` rows from the same execution.
3. Scope remains clean to task-owned backend/doc paths.
4. Controller can close task-07 without waiting for SURGICAL.

## Avoid repeating

- Do not hold task-07 for SURGICAL ACCEPT/REVISE (disabled).
- Do not run repeated unchanged expensive cycles without producing closure-complete evidence.
