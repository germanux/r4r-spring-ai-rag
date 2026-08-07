# Backend ↔ Frontend handoff

## Concurrency and scope check

- **PC active scope:** backend/doc paths (`pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`) under `task-07-populate-production-rag`.
- **LP active scope:** single frontend spec file under `task-fe-03d-dom-state-tests`.
- **Overlap assessment:** none. Disjoint backend/frontend scopes are safe for concurrent continuation.

## Backend status for frontend awareness

- Backend task-07 has gate-green evidence but remains non-accepted due to closure incompleteness (`checkpoint_head` missing in current request evidence).
- Frontend should not wait on backend changes for FE-03D because LP work is self-contained in component DOM unit tests.

## Frontend status for backend awareness

- FE-03D currently red with deterministic correction instructions and a single-file edit scope.
- Backend should continue independently; no backend path is blocked by current frontend failure.

## Directed next actions

1. **[Level 2, PC, task-07-populate-production-rag]**
   - **Dependencies:** accepted task-06f baseline.
   - **allowed_paths:** `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`.
   - **Exact gate:** `git diff --check` then the canonical task-07 command from `.opencode/task-plan.backend.json`.
   - **Acceptance evidence:** gate exit 0, scope-clean diff, closure-ready metadata for controller checkpoint/commit.

2. **[Level 1, LP, task-fe-03d-dom-state-tests]**
   - **Dependencies:** task-fe-03c accepted.
   - **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts`.
   - **Exact gate:** `git diff --check` then `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`.
   - **Acceptance evidence:** FE-03D gate green with preserved prior valid test coverage and prescribed DOM assertions.
