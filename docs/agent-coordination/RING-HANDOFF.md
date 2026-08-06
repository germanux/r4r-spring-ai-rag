# Backend ↔ Frontend handoff

## Queue separation decision

- **Backend owner (PC path):** `task-07-populate-production-rag`
- **Frontend owner (LP path):** `task-fe-03d-dom-state-tests`
- **Scope overlap check:** none in current evidence-backed actions.

Current bounded scopes are disjoint:

- Backend work references `src/main/**`, `src/test/**`, `docs/backend/**`.
- Frontend LP correction is restricted to `frontend/src/app/features/rag/rag-page.component.spec.ts`.

## Required routing for this cycle

### 1) Backend package

- **Implementation level:** 3
- **Assigned role:** SURGICAL
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:** existing PC gate-green checkpoint request + mandatory review policy
- **allowed_paths:** review-only pass on current checkpoint evidence; no new PC implementation until verdict
- **Exact gate constraint:** preserve canonical task-07 gate from `.opencode/task-plan.backend.json`
- **Required SURGICAL review:** immediate, explicit `ACCEPT` or `REVISE`

### 2) Frontend package

- **Implementation level:** 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** Codex `REVISE` packet already issued for this task
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate:** `git diff --check` then `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
- **Required SURGICAL review:** post-gate review is mandatory before closure

## Integration risk controls

1. Hold backend implementation churn while task-07 review verdict is pending to avoid redundant gate-green loops.
2. Keep LP correction strictly single-file and assertion-focused; do not widen into production component changes.
3. Enforce evidence consistency: the same attempt must provide matching understanding + gate summary + status for deterministic closure.
