# PC code review (Ring)

## Evidence reviewed

- `runtime/ring-agent/ring/20260807T020031Z/worker-requests/PC.json`
- `runtime/ring-agent/ring/20260807T020031Z/worker-request-manifest.json`
- `runtime/ring-agent/ring/20260807T020031Z/pc-runtime/progress.json`
- `runtime/ring-agent/ring/20260807T020031Z/pc-runtime/memory.md`
- `runtime/ring-agent/ring/20260807T020031Z/pc-git-status.txt`

## First current defect

Task-07 has a gate-green checkpoint request (`gate_exit: 0`) but closure evidence is incomplete:

- `codex_decision: null`
- `next_action: null`
- `checkpoint_head: null`
- progress still shows `task-07-populate-production-rag` as `BLOCKED`

This is a closure-quality/evidence defect, not a new feature request.

## Bounded next work package

- **Implementation level:** Level 2
- **Assigned role:** PC
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:** `task-06f-ingestion-validation: ACCEPTED` (already satisfied)
- **allowed_paths:** `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`
- **Objective:** Convert gate-green execution into complete closure evidence for task-07.

### One-pass action

1. Keep existing backend scope only (no scope expansion).
2. Run `git diff --check`.
3. Run the exact task-07 gate once.
4. Return non-null closure metadata (`codex_decision`, `next_action`, `checkpoint_head`) and explicit `vector_store` row-count proof.

### Exact gate

```bash
bash -lc "rm -rf target && ./scripts/task-gate.sh all && set -a && source ./.env && set +a && mvn -q -DskipTests spring-boot:run -Dspring-boot.run.main-class=com.riansares.r4r.ingestion.KnowledgeIngestionCli && rows=$(docker exec \"${POSTGRES_APP_CONTAINER:-r4r-postgres-app}\" psql -U \"${POSTGRES_APP_USER:-r4r}\" -d \"${POSTGRES_APP_DB:-r4r_rag}\" -Atqc 'SELECT count(*) FROM vector_store') && test \"$rows\" -gt 0"
```

### Acceptance conditions

- Exact gate exits 0 in the current pass.
- Diff remains inside `allowed_paths`.
- Closure metadata fields are non-null in worker request evidence.
- Closure policy satisfied: `exact-gate-green + scope-clean + controller-commit`.

### Avoid repeating

Do not submit another gate-green checkpoint payload with null closure fields.
