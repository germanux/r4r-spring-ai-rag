# Worker understanding check

## PC understanding status

Evidence indicates PC successfully executed the task-07 gate path (`gate_exit=0`) but did not complete closure reporting fields. This suggests execution-level understanding is adequate, while controller-facing completion semantics are incomplete.

### Required understanding for next pass

- A gate-green checkpoint is not closure by itself.
- Worker request metadata must be complete and non-null for closure handling.
- Task-07 must include explicit row-count proof (`vector_store`) in returned evidence.

## LP understanding status

Evidence indicates LP has a precise correction packet but prior local understanding/execution quality was insufficient:

- gate summary is failing (`exit 2`)
- prior run timed out
- Codex packet explicitly flags misunderstood/forbidden patterns

### Required understanding for next pass

- Keep FE-03D correction strictly one-file and DOM-observable.
- Use the specified selectors/assertion mapping (`.loading-state`, `.error-state`, `.answer-content`, `.citations-section`, `.idle-state`, disabled controls).
- Preserve existing valid tests; do not introduce internal-state mutation patterns.

## Bounded directives

1. **PC / Level 2 / `task-07-populate-production-rag`**
   - dependencies: `task-06f-ingestion-validation: ACCEPTED`
   - allowed_paths: `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`
   - exact gate: task-07 backend gate command (plus `git diff --check`)

2. **LP / Level 1 / `task-fe-03d-dom-state-tests`**
   - dependencies: `task-fe-03c-citations: ACCEPTED`
   - allowed_paths: `frontend/src/app/features/rag/rag-page.component.spec.ts`
   - exact gate: `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests` (after `git diff --check`)
