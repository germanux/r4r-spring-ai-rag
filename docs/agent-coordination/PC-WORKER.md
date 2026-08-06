# PC Code Review (Ring)

## Current evidence reviewed
- `runtime/ring-agent/ring/20260806T171721Z/pc-runtime/progress.json`
- `runtime/ring-agent/ring/20260806T171721Z/pc-runtime/gate_summary.md`
- `runtime/ring-agent/ring/20260806T171721Z/pc-git-status.txt`
- `runtime/ring-agent/ring/20260806T171721Z/pc-git-diff-stat.txt`
- `runtime/ring-agent/ring/20260806T171721Z/pc-runtime/previous-ring-qwen3-directive.json`

## First current defect
PC is carrying active backend code edits while the parent implementation path is dependency-blocked (`BE-07-B` requires `BE-07-A:ACCEPTED`), and the latest deterministic backend gate evidence is red (`test-failure`, exit 1). The first defect is sequencing/control failure, not missing additional implementation.

## Decision
- **Implementation level:** Level 2 (PC), but currently **held**.
- **Assigned role:** PC (backend).
- **Task ID:** `task-07-populate-production-rag`.
- **Action:** `HOLD`.

## Bounded next action package
- **Owner:** SURGICAL reviewer (mandatory before closure).
- **Why SURGICAL now:** Queue ambiguity (blocked dependency + fresh backend edits + failing gate) needs a keep/revert decision to prevent repeated low-signal PC loops.
- **Dependencies:**
  - `BE-07-A:ACCEPTED` before any BE-07-B-style backend implementation continues.
- **allowed_paths (for any future PC implementation pass):**
  - From backend task plan: `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`.
- **Exact gate (when unblocked):**
  - `bash -lc "rm -rf target && ./scripts/task-gate.sh all && set -a && source ./.env && set +a && mvn -q -DskipTests spring-boot:run -Dspring-boot.run.main-class=com.riansares.r4r.ingestion.KnowledgeIngestionCli && rows=$(docker exec \"${POSTGRES_APP_CONTAINER:-r4r-postgres-app}\" psql -U \"${POSTGRES_APP_USER:-r4r}\" -d \"${POSTGRES_APP_DB:-r4r_rag}\" -Atqc 'SELECT count(*) FROM vector_store') && test \"$rows\" -gt 0"`
- **Required review:** SURGICAL Codex `ACCEPT` remains mandatory.

## Acceptance conditions for this coordination cycle
1. PC receives no new coding directive until dependency is clear.
2. Existing backend failing evidence is preserved for SURGICAL review.
3. No scope expansion beyond active backend task.

## Avoid repeating
- Do not run more backend implementation/gate loops under `task-07` while dependency and red evidence remain unresolved.
