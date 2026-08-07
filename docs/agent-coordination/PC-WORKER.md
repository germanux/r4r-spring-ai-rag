# PC code review — run 20260807T013028Z

## Current evidence read

- `runtime/ring-agent/ring/20260807T013028Z/pc-runtime/progress.json`
- `runtime/ring-agent/ring/20260807T013028Z/pc-runtime/gate_summary.md`
- `runtime/ring-agent/ring/20260807T013028Z/worker-requests/PC.json`
- `runtime/ring-agent/ring/20260807T013028Z/worker-request-manifest.json`
- `runtime/ring-agent/ring/20260807T013028Z/pc-git-status.txt`

## Diagnosis (first current defect)

The backend gate is currently green (`exit 0`), but task closure evidence is incomplete: `progress.json` still records `task-07-populate-production-rag` as `BLOCKED`, and the checkpoint request has `codex_decision: null` and `next_action: null`.

This is not a request for new architecture or widened scope; it is a closure-quality defect in the current task pass evidence.

## Bounded next package

- **Implementation level:** Level 2
- **Assigned role:** PC
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:** `task-06f-ingestion-validation: ACCEPTED` (already satisfied in progress ledger)
- **allowed_paths (canonical write scope):**
  - `pom.xml`
  - `src/main/**`
  - `src/test/**`
  - `docs/backend/**`
- **One focused action for one pass:**
  1. Keep current backend-only scope.
  2. Run `git diff --check` first.
  3. Run the exact task-07 gate once.
  4. Return closure-complete evidence that explicitly includes command exit and non-zero `vector_store` row proof.

## Exact gate

1. `git diff --check`
2. `bash -lc "rm -rf target && ./scripts/task-gate.sh all && set -a && source ./.env && set +a && mvn -q -DskipTests spring-boot:run -Dspring-boot.run.main-class=com.riansares.r4r.ingestion.KnowledgeIngestionCli && rows=$(docker exec \"${POSTGRES_APP_CONTAINER:-r4r-postgres-app}\" psql -U \"${POSTGRES_APP_USER:-r4r}\" -d \"${POSTGRES_APP_DB:-r4r_rag}\" -Atqc 'SELECT count(*) FROM vector_store') && test \"$rows\" -gt 0"`
3. Hierarchy closure policy: `exact-gate-green + scope-clean + controller-commit`

## Acceptance evidence required

- Gate exit code `0`.
- Explicit non-zero `vector_store` count proof in captured diagnostics/evidence.
- No write-scope drift outside allowed backend paths.

## Avoid repeating

Do **not** submit another gate-green checkpoint packet without closure metadata/diagnostics; that reproduces the same blocked loop.
