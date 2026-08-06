# Backend ↔ Frontend handoff

## Queue status

- **Backend (PC task-07): HOLD**
  - Reason: dependency sequencing and red-gate unresolved backend diff.
  - No new PC coding pass authorized this cycle.

- **Frontend (LP task-fe-03d): CONTINUE**
  - Reason: pending task with unresolved Codex REVISE and no-product-diff latest attempt.
  - One bounded LP revise pass is authorized.

## Disjoint ownership and write scopes

- Backend package context (blocked):
  - Task ID: `task-07-populate-production-rag`
  - Level: 2 execution blocked; Level 3 review required now
  - allowed_paths (eventual PC execution): `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`

- Frontend package context (active):
  - Task ID: `task-fe-03d-dom-state-tests`
  - Level: 1
  - allowed_paths (this pass): `frontend/src/app/features/rag/rag-page.component.spec.ts`

No overlapping write scope is permitted between these two actions.

## Integration risk control

1. **Backend hold prevents invalid sequencing** (`BE-07-B` before `BE-07-A`).
2. **Frontend continue prevents idle queue time** while backend waits on prerequisite acceptance.
3. **Mandatory SURGICAL review remains required for both queues before closure.**

## Exact gates to enforce

- Backend (when unblocked): task-07 backend exact gate from `.opencode/task-plan.backend.json`.
- Frontend (current pass):
  - `git diff --check`
  - `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
