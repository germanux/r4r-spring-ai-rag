# Backend ↔ Frontend handoff

## Queue separation decision

No overlapping write scopes are present in this cycle.

- **Backend active item:** `task-06f-ingestion-validation` (PC queue, review-state only)
  - backend scope if revise is needed: `src/test/resources/application.yml`, `.opencode/current/PC/**`
- **Frontend active item:** `task-fe-03c-citations` (LP queue, FE-03C-A)
  - frontend scope: `frontend/src/app/features/rag/rag-page.component.spec.ts`

These scopes are disjoint; concurrent queue progression is safe.

## Cross-stack dependency posture

- Frontend FE-03C does not require backend code changes in this pass.
- Backend task-06f currently requires SURGICAL review evidence, not implementation work.
- No cross-layer migration/security/lifecycle ambiguity was identified that forces a new level-3 product patch.

## Bounded next actions

1. **SURGICAL review pass (Level 3, reviewer role)** for backend `task-06f-ingestion-validation` gate-green package.
   - Gate constraint: `./scripts/task-gate.sh task-06f-ingestion-validation` remains exit 0.
   - Closure condition: explicit SURGICAL `ACCEPT` evidence.

2. **LP implementation pass (Level 1, LP role)** for frontend `FE-03C-A`.
   - allowed_paths: `frontend/src/app/features/rag/rag-page.component.spec.ts`.
   - Gates: `git diff --check` and `./scripts/frontend-task-gate.sh task-fe-03c-citations`.
   - Closure condition: SURGICAL `ACCEPT` after gate-green evidence.

## Hold conditions

- If LP findings require component/template edits beyond FE-03C-A scope, pause LP queue and route a new scoped package before proceeding.
- If SURGICAL returns backend `REVISE`, dispatch exactly one bounded BE-06F-A correction pass before any further backend advancement.
