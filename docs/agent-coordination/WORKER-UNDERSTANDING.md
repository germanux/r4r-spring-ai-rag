# Worker understanding calibration

## PC understanding checkpoint

- **Task:** `task-07-populate-production-rag`
- **What is proven:** prior pass produced `gate_exit=0` request.
- **What is not yet proven in current snapshot:** closure-complete acceptance evidence (request metadata/progress still indicate non-closed state).
- **Required next understanding:** this is a closure-quality pass, not scope expansion. Keep to backend/doc allowed paths and emit deterministic evidence that controller can close.

### PC bounded directive
- **Implementation level:** Level 2
- **Owner:** PC
- **Dependencies:** existing backend accepted chain
- **allowed_paths:** `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`
- **Exact gate:** `git diff --check` then exact task-07 command

## LP understanding checkpoint

- **Task:** `task-fe-03d-dom-state-tests`
- **What is proven:** only one edited spec file with failed gate and Codex revise packet.
- **Primary misunderstanding to correct:** using prohibited synthetic/manual patterns and destabilizing test structure instead of DOM-first assertions.
- **Required next understanding:** map each requirement to stable selectors/assertions only:
  - loading status → `.loading-state[role="status"]`
  - disabled controls → `textarea`, `.submit-button`
  - transport failure → `.error-state[role="alert"]`
  - answer rendering → `.answer-content`
  - reset cleanup → absence of answer/error/citations + presence of `.idle-state`

### LP bounded directive
- **Implementation level:** Level 1
- **Owner:** LP
- **Dependencies:** `task-fe-03c-citations:ACCEPTED`
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate:** `git diff --check` then `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`

## Shared non-negotiables

- No Git history operations by workers.
- No scope widening.
- Closure is controller-owned only after exact-gate-green + scope-clean.
