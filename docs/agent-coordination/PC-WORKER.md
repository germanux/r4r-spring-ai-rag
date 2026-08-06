# PC code review (backend queue)

## Current evidence snapshot

- Active backend task: `task-07-populate-production-rag` (`pc-runtime/progress.json`).
- Deterministic gate status: **green**, exit `0` (`pc-runtime/gate_summary.md`).
- First current defect: controller status is **`CHECKPOINT_COMMIT_FAILED`** with error `Automatic gate-green checkpoint commit failed` (`pc-runtime/controller_state.json`).
- Worker request exists but `codex_decision` is still `null` (`worker-requests/PC.json`).

## Diagnosis

PC implementation evidence is gate-green, but closure is blocked by the post-gate checkpoint/acceptance chain. This is not a new backend feature request; it is a review-and-integration control defect on the current checkpoint path.

## Bounded next action package

- **Implementation level:** Level 3 (SURGICAL Codex review-only pass)
- **Assigned role:** SURGICAL (`r4r-surgical-architect`/`r4r-surgical-fixer` as needed)
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:**
  - Deterministic gate already green for attempt `1` (`pc-runtime/gate_summary.md`)
  - Existing checkpoint/request evidence (`pc-runtime/checkpoint.json`, `worker-requests/PC.json`)
- **allowed_paths (for any follow-up fix, if review finds one):**
  - Keep backend scope bounded to the task plan authority: `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`.
  - If the issue is controller/workflow-only, route a separate level-3 operational package; do **not** widen PC product scope.
- **Exact gate/constraint:**
  - `.opencode/task-plan.backend.json` task-07 gate command remains authoritative:
    - `bash -lc "rm -rf target && ./scripts/task-gate.sh all && set -a && source ./.env && set +a && mvn -q -DskipTests spring-boot:run -Dspring-boot.run.main-class=com.riansares.r4r.ingestion.KnowledgeIngestionCli && rows=$(docker exec \"${POSTGRES_APP_CONTAINER:-r4r-postgres-app}\" psql -U \"${POSTGRES_APP_USER:-r4r}\" -d \"${POSTGRES_APP_DB:-r4r_rag}\" -Atqc 'SELECT count(*) FROM vector_store') && test \"$rows\" -gt 0"`
  - `.opencode/task-plan.hierarchy.json` closure: `exact-gate-green + scope-clean + surgical-accept + controller-commit`.
- **Required SURGICAL review:** Mandatory before closure (hierarchy policy).

## Acceptance evidence required next cycle

1. Codex decision on this checkpoint request is explicit (`ACCEPT` or `REVISE`) and no longer null.
2. Controller commit path is either successful or the failure is classified with a bounded surgical correction package.
3. No new PC edit/gate loop unless review/classification requires it.

## Avoid repeating

- Do not run another unchanged PC implementation pass while `codex_decision=null` and checkpoint commit failure remains unresolved.
