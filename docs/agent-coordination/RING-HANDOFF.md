# Backend ↔ Frontend Handoff

## Queue status snapshot
- **Backend (PC):** HOLD on `task-07-populate-production-rag` pending prerequisite sequencing (`BE-07-A` acceptance required before BE-07 execution work).
- **Frontend (LP):** REVIEW on `task-fe-03d-dom-state-tests` with gate-green checkpoint awaiting SURGICAL Codex decision.

## Disjoint ownership and scope control
### Backend package
- **Level:** 2
- **Owner:** PC
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:** `BE-07-A:ACCEPTED`
- **allowed_paths (when unblocked):** `src/**`, `docs/backend/**`
- **Gate (when unblocked):** `./scripts/task-gate.sh all`
- **SURGICAL requirement:** ACCEPT required for closure

### Frontend package
- **Level:** 1 (under mandatory review)
- **Owner:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** `task-fe-03c-citations:ACCEPTED` (already satisfied)
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Gate:** `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests` (already green at attempt 6)
- **SURGICAL requirement:** Codex `ACCEPT` still required

## Integration risks and controls
1. **Risk:** PC backend work resumes early and violates BE-07 dependency order.
   - **Control:** keep PC HOLD until explicit evidence of `BE-07-A` acceptance.
2. **Risk:** LP checkpoint remains unreviewed, causing stale context and redundant reruns.
   - **Control:** prioritize immediate SURGICAL review for LP attempt 6.
3. **Risk:** cross-queue scope bleed.
   - **Control:** preserve strict backend/frontend allowed_paths disjointness and single-owner passes.

## Immediate bounded next actions
1. Route LP checkpoint for SURGICAL review now.
2. Keep PC queue paused (no gate rerun, no scope widening) until dependency evidence changes.
