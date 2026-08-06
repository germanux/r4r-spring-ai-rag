# PC code review (evidence cycle: 20260806T174553Z)

## Current evidence read
- `pc-runtime/progress.json`: active task is `task-07-populate-production-rag` and status is `BLOCKED`.
- `pc-runtime/gate_summary.md`: deterministic gate classification is `test-failure`, exit `1`.
- `pc-git-status.txt` + `pc-git-diff-stat.txt`: backend product files are still dirty (`KnowledgeIngestionService`, `PgVectorKnowledgeStore`, related tests).
- `pc-runtime/previous-ring-qwen3-directive.json`: prior ring directive already held PC pending dependency and SURGICAL disposition.

## First current defect
PC is still in task-07 implementation while its hierarchy prerequisite is unresolved:
- `BE-07-B` (PC execution package) depends on `BE-07-A:ACCEPTED`.
- No evidence in this RUN_DIR proves `BE-07-A` acceptance or a new SURGICAL keep/revert decision on the red diff.

This is a sequencing defect (dependency and review closure), not a request for another uncontrolled implementation loop.

## Bounded next action package
- **Implementation level:** Level 3
- **Assigned role:** SURGICAL Codex (review-only disposition)
- **Task ID:** `task-07-populate-production-rag` (dependency check anchored to `BE-07-B`)
- **Dependencies:** `BE-07-A:ACCEPTED` required before resuming PC execution package `BE-07-B`
- **allowed_paths:** review-only disposition first; if reopened for PC later, constrained by backend task scope (`pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**` from task plan)
- **Exact gate (when PC resumes):**
  `bash -lc "rm -rf target && ./scripts/task-gate.sh all && set -a && source ./.env && set +a && mvn -q -DskipTests spring-boot:run -Dspring-boot.run.main-class=com.riansares.r4r.ingestion.KnowledgeIngestionCli && rows=$(docker exec \"${POSTGRES_APP_CONTAINER:-r4r-postgres-app}\" psql -U \"${POSTGRES_APP_USER:-r4r}\" -d \"${POSTGRES_APP_DB:-r4r_rag}\" -Atqc 'SELECT count(*) FROM vector_store') && test \"$rows\" -gt 0"`
- **Required SURGICAL review:** mandatory `ACCEPT` before closure; no closure claim without it.

## Hold instruction for PC queue
Do **not** run another PC implementation/gate pass until:
1. `BE-07-A` is accepted with evidence, and
2. SURGICAL returns explicit disposition for the current red-gate backend diff.

## Acceptance evidence required next
- Dependency satisfaction evidence for `BE-07-A`.
- SURGICAL review output with keep/revert guidance for current modified backend files.
- After resume: fresh gate evidence for task-07 and Codex `ACCEPT`.
