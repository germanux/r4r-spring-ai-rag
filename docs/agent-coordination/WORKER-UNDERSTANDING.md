# Worker understanding check

## PC understanding requirement

- **Level / role / task:** Level 2, PC, `task-07-populate-production-rag`
- **Core understanding to demonstrate:** the failing point is closure metadata, not task-gate correctness.
- **Dependencies:** prior accepted backend chain through `task-06f-ingestion-validation`.
- **allowed_paths:** `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`
- **Exact gate:**
  - `git diff --check`
  - task-07 exact gate command from `.opencode/task-plan.backend.json`
- **Acceptance condition:** controller/checkpoint artifacts must prove successful closure commit after a green gate.

### Misunderstanding to avoid

Do not treat another gate-green result alone as sufficient while `checkpoint.json` remains failed or `head_after` remains null.

## LP understanding requirement

- **Level / role / task:** Level 1, LP, `task-fe-03d-dom-state-tests`
- **Core understanding to demonstrate:** execute only the prescribed one-file DOM-test correction packet after attempt rearm.
- **Dependencies:** attempt-budget reset/rearm + `task-fe-03c-citations:ACCEPTED`.
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate:**
  - `git diff --check`
  - `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
- **Acceptance condition:** corrected spec structure, three targeted tests added, exact gate green, scope clean, controller commit evidence.

### Misunderstanding to avoid

Do not broaden scope, mutate component internals/HTML directly, or invent selectors/state values that are not part of the current component contract.
