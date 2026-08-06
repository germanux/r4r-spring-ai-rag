# PC code review — run 20260806T195634Z

## Evidence reviewed

- `runtime/ring-agent/ring/20260806T195634Z/worker-request-manifest.json`
- `runtime/ring-agent/ring/20260806T195634Z/worker-requests/PC.json`
- `runtime/ring-agent/ring/20260806T195634Z/pc-runtime/progress.json`
- `runtime/ring-agent/ring/20260806T195634Z/pc-runtime/previous-ring-qwen3-directive.json`
- `runtime/ring-agent/ring/20260806T195634Z/pc-git-status.txt`

## First current defect (PC)

Task closure evidence is incomplete, not a newly demonstrated code failure:

- Task: `task-07-populate-production-rag`
- Current request shows `gate_exit: 0` (green) but `codex_decision: null` and `checkpoint_head: null`.
- Progress still marks task-07 as `BLOCKED`.

This means the queue cannot close task-07 yet under hierarchy closure rules.

## Bounded next action package

- **Implementation level:** 3 (SURGICAL)
- **Assigned role:** SURGICAL Codex (`r4r-surgical-architect` / `r4r-surgical-fixer`)
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:**
  - `task-06f-ingestion-validation:ACCEPTED` (already true in progress evidence)
  - Existing task-07 gate-green request evidence (current attempt)
- **allowed_paths:**
  - Review-only pass: no product-file writes.
  - If correction is required after review: constrained to backend task scope from plan (`pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`).
- **Exact gate:**
  - `bash -lc "rm -rf target && ./scripts/task-gate.sh all && set -a && source ./.env && set +a && mvn -q -DskipTests spring-boot:run -Dspring-boot.run.main-class=com.riansares.r4r.ingestion.KnowledgeIngestionCli && rows=$(docker exec \"${POSTGRES_APP_CONTAINER:-r4r-postgres-app}\" psql -U \"${POSTGRES_APP_USER:-r4r}\" -d \"${POSTGRES_APP_DB:-r4r_rag}\" -Atqc 'SELECT count(*) FROM vector_store') && test \"$rows\" -gt 0"`
- **Required SURGICAL review:** mandatory `ACCEPT` or `REVISE` before controller closure.

## Acceptance conditions for this pass

1. SURGICAL review decision is no longer null for the active task-07 request.
2. Controller closure evidence satisfies hierarchy policy: `exact-gate-green + scope-clean + surgical-accept + controller-commit`.
3. No new PC implementation loop is started unless SURGICAL explicitly returns `REVISE` with bounded instructions.

## Avoid repeating

Do **not** run another unchanged PC implementation/gate cycle while review/closure evidence remains unresolved.
