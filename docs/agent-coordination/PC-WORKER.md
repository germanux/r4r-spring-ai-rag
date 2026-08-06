# PC code review (evidence cycle 20260806T184628Z)

## Current evidence read
- `pc-runtime/progress.json`: active task is `task-07-populate-production-rag`; status remains `BLOCKED`; last gate-green attempt recorded.
- `worker-requests/PC.json`: controller requested handling for a **gate-green checkpoint** with `codex_decision: null`.
- `pc-runtime/memory.md`: latest gate exit `0`; Codex decision still pending.
- `pc-git-status.txt`: backend/product changes are present and not yet controller-closed.

## First current defect (PC)
The backend pass is **not closure-ready** because SURGICAL disposition is missing for the current task-07 diff/evidence packet. No current-run artifact proves `ACCEPT` or final controller commit.

## Bounded next action package
- **Implementation level:** 3 (SURGICAL review lane)
- **Assigned role:** SURGICAL Codex (`r4r-surgical-architect` / `r4r-surgical-fixer` review-only disposition)
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:**
  - `task-06f-ingestion-validation:ACCEPTED` (already shown)
  - Validate hierarchy dependency alignment for `BE-07-B` requiring `BE-07-A:ACCEPTED` before closure.
- **allowed_paths (for any follow-up implementation after disposition):**
  - from backend plan task-07: `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`
  - from hierarchy BE-07-B: `src/**`, `docs/backend/**`
- **Exact gate:**
  - `bash -lc "rm -rf target && ./scripts/task-gate.sh all && set -a && source ./.env && set +a && mvn -q -DskipTests spring-boot:run -Dspring-boot.run.main-class=com.riansares.r4r.ingestion.KnowledgeIngestionCli && rows=$(docker exec \"${POSTGRES_APP_CONTAINER:-r4r-postgres-app}\" psql -U \"${POSTGRES_APP_USER:-r4r}\" -d \"${POSTGRES_APP_DB:-r4r_rag}\" -Atqc 'SELECT count(*) FROM vector_store') && test \"$rows\" -gt 0"`
- **Required SURGICAL review:** mandatory before closure per `.opencode/task-plan.hierarchy.json` (`exact-gate-green + scope-clean + surgical-accept + controller-commit`).

## Acceptance evidence expected next
1. A recorded SURGICAL `ACCEPT` or `REVISE` tied to this exact task-07 checkpoint.
2. If `REVISE`, one bounded follow-up correction pass only.
3. No dependency bypass around BE-07-A/BE-07-B sequencing.

## Avoid repeating
- Do not start a fresh PC coding/gate loop on task-07 without first obtaining the SURGICAL disposition for the already gate-green backend diff.
