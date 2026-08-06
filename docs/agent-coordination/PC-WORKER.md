# PC code review (evidence cycle: 20260806T184128Z)

## Current evidence read
- `worker-request-manifest.json` and `worker-requests/PC.json`: PC submitted a `gate-green-checkpoint` request for `task-07-populate-production-rag` with non-empty backend `changed_paths`.
- `pc-runtime/checkpoint.json`: gate exit is `0`, but checkpoint status is `failed` and `head_after` is null.
- `pc-runtime/controller_state.json`: controller status is `CHECKPOINT_COMMIT_FAILED` (exit code `67`), error `Automatic gate-green checkpoint commit failed`.
- `pc-runtime/progress.json`: active task remains `task-07-populate-production-rag` with status `BLOCKED`.
- `pc-runtime/gate_summary.md`: reports `test-failure` exit `1`, which conflicts with the gate-green checkpoint packet for the same run snapshot.

## First current defect
The immediate defect is **closure-state inconsistency**, not a new implementation target:
1. PC has a gate-green request with real backend edits,
2. but there is no SURGICAL Codex disposition yet,
3. and controller checkpoint commit failed,
4. while packaged diagnostics are internally inconsistent (gate summary says red).

This is integration-risk work and must be dispositioned through SURGICAL before another PC coding pass.

## Bounded next action package
- **Implementation level:** Level 3
- **Assigned role:** SURGICAL Codex
- **Task ID:** `task-07-populate-production-rag` (hierarchy dependency context: `BE-07-B`)
- **Dependencies:**
  - `BE-07-B` depends on `BE-07-A:ACCEPTED` per `.opencode/task-plan.hierarchy.json`.
  - Mandatory reviewer policy: SURGICAL review is required before closure.
- **allowed_paths:**
  - This pass is review-only disposition (no product edits by Ring).
  - If reopened for implementation, enforce backend scope from task plan: `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`.
- **Exact gate (for any resumed PC implementation):**
  - `bash -lc "rm -rf target && ./scripts/task-gate.sh all && set -a && source ./.env && set +a && mvn -q -DskipTests spring-boot:run -Dspring-boot.run.main-class=com.riansares.r4r.ingestion.KnowledgeIngestionCli && rows=$(docker exec \"${POSTGRES_APP_CONTAINER:-r4r-postgres-app}\" psql -U \"${POSTGRES_APP_USER:-r4r}\" -d \"${POSTGRES_APP_DB:-r4r_rag}\" -Atqc 'SELECT count(*) FROM vector_store') && test \"$rows\" -gt 0"`
- **Required SURGICAL review:** explicit `ACCEPT` or `REVISE` with keep/revert guidance for the current dirty backend paths and checkpoint-commit-failed state.

## Queue instruction
Do not dispatch a fresh PC implementation loop until SURGICAL publishes disposition on this checkpoint packet and evidence inconsistency.

## Acceptance evidence required next
1. SURGICAL disposition for current backend diff (`ACCEPT`/`REVISE` + keep/revert guidance).
2. Reconciled checkpoint/diagnostic evidence (single authoritative gate outcome per run packet).
3. Controller-owned commit path confirmed before closure claim.
