# PC code review (Ring)

## Current evidence snapshot

- Active backend task in progress file: `task-07-populate-production-rag` with status `BLOCKED` (`pc-runtime/progress.json`).
- Latest deterministic gate classification: `test-failure`, exit `1` (`pc-runtime/gate_summary.md`).
- Dirty PC backend paths remain (`pc-git-status.txt`):
  - `src/main/java/com/riansares/r4r/ingestion/KnowledgeIngestionService.java`
  - `src/main/java/com/riansares/r4r/vector/PgVectorKnowledgeStore.java`
  - `src/test/java/com/riansares/r4r/ingestion/KnowledgeIngestionServiceIT.java`
  - `src/test/java/com/riansares/r4r/ingestion/TestChildApplicationContextInitializer.java`

## First current defect

PC is positioned on implementation work that is dependency-blocked by hierarchy sequencing and currently red.

- Hierarchy dependency: **BE-07-B** (PC) requires **BE-07-A:ACCEPTED** first.
- Current hierarchy state for **BE-07-A** is still `PENDING`.
- Continuing PC code churn now would repeat a known blocked path and increase integration risk.

## Directed action package

- **Implementation level:** Level 3 (SURGICAL)
- **Assigned role:** SURGICAL Codex reviewer (OpenCode)
- **Task ID:** `task-07-populate-production-rag` (dependency context: `BE-07-A` -> `BE-07-B`)
- **Dependencies:**
  - `BE-07-A:ACCEPTED` before PC implementation on `BE-07-B`
  - Existing red-gate PC diff must receive keep/revert disposition
- **allowed_paths (for eventual PC execution when unblocked):** from backend task plan
  - `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`
- **Immediate exact gate for this cycle:** review-only hold (no new PC gate run)
- **Required SURGICAL review:** mandatory before any PC closure or reopen

## Acceptance conditions before PC resumes coding

1. SURGICAL issues an explicit disposition on the current dirty backend diff (keep/revise/revert).
2. Dependency condition is satisfied: `BE-07-A` accepted.
3. PC then runs the exact task-07 gate:
   - `bash -lc "rm -rf target && ./scripts/task-gate.sh all && set -a && source ./.env && set +a && mvn -q -DskipTests spring-boot:run -Dspring-boot.run.main-class=com.riansares.r4r.ingestion.KnowledgeIngestionCli && rows=$(docker exec \"${POSTGRES_APP_CONTAINER:-r4r-postgres-app}\" psql -U \"${POSTGRES_APP_USER:-r4r}\" -d \"${POSTGRES_APP_DB:-r4r_rag}\" -Atqc 'SELECT count(*) FROM vector_store') && test \"$rows\" -gt 0"`
4. Closure still requires SURGICAL `ACCEPT`.

## Avoid repeating

Do not run another PC implementation/gate cycle on task-07 while dependency BE-07-A is unaccepted and the current red diff is unresolved.
