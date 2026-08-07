# Backend ↔ Frontend handoff

## Concurrency and scope safety

- **PC stream (backend):** `task-07-populate-production-rag`, Level 2, allowed scope `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`.
- **LP stream (frontend):** `task-fe-03d-dom-state-tests`, Level 1, practical scope this pass is one file: `frontend/src/app/features/rag/rag-page.component.spec.ts`.
- Write scopes are disjoint (`src/**` + `docs/backend/**` vs `frontend/**`), so both queues can continue without overlap hold.

## Current cross-stack state

- Backend has gate-green evidence request but no closure completion yet for task-07.
- Frontend has a known local test defect set with explicit corrective instructions.

## Directed next actions

1. **PC / Level 2 / task-07-populate-production-rag**
   - **Dependencies:** `task-06f-ingestion-validation` accepted.
   - **allowed_paths:** `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`.
   - **Exact gate:**
     - `git diff --check`
     - task-07 backend command from `.opencode/task-plan.backend.json`
   - **Acceptance condition:** gate green + scope clean + controller commitability.

2. **LP / Level 1 / task-fe-03d-dom-state-tests**
   - **Dependencies:** `task-fe-03c-citations` accepted.
   - **allowed_paths:** `frontend/**`, `docs/frontend/**` (single-file correction expected).
   - **Exact gate:**
     - `git diff --check`
     - `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
   - **Acceptance condition:** FE-03D gate green with corrected DOM-state tests only.

## Integration risks to watch next cycle

- Repeated backend closure stalls despite gate-green requests can delay task-08 backend start.
- Repeated FE-03D spec defects can delay FE-03e security/accessibility sequencing.
