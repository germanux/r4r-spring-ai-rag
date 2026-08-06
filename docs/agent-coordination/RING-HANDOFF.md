# Backend ↔ Frontend handoff (Ring)

## Queue status snapshot

### Backend (PC queue)

- **Task:** `task-07-populate-production-rag`
- **State from evidence:** gate-green request is present, but closure is blocked pending SURGICAL disposition (`codex_decision: null`).
- **Decision this cycle:** `HOLD` PC implementation; run SURGICAL review-only pass.

### Frontend (LP queue)

- **Task:** `task-fe-03d-dom-state-tests`
- **State from evidence:** red gate (`exit 2`) with Codex `REVISE` and explicit, spec-only correction guidance.
- **Decision this cycle:** `CONTINUE` with one bounded LP repair pass.

## Ownership disjointness and dependency control

- Backend and frontend write scopes remain disjoint in this cycle:
  - Backend review concerns `src/**` + `docs/backend/**` evidence for task-07.
  - Frontend correction is constrained to `frontend/src/app/features/rag/rag-page.component.spec.ts`.
- No cross-queue product-path overlap is authorized.
- Any newly discovered cross-layer or ambiguous requirement must be escalated to **Level 3 SURGICAL** and the overlapping queue held.

## Action packages for this cycle

1. **PKG-PC-07-REVIEW-ONLY**
   - Level 3, owner SURGICAL, task `task-07-populate-production-rag`
   - Dependency: existing gate-green request evidence
   - allowed_paths: review-only evidence pass (no product writes)
   - gate/constraint: closure policy (`exact-gate-green + scope-clean + surgical-accept + controller-commit`)
   - required SURGICAL review: yes (this is that review)

2. **PKG-LP-FE03D-SPEC-REPAIR**
   - Level 1, owner LP, task `task-fe-03d-dom-state-tests`
   - Dependency: active REVISE packet + accepted `task-fe-03c-citations`
   - allowed_paths: `frontend/src/app/features/rag/rag-page.component.spec.ts`
   - exact gate: `git diff --check` then `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
   - required SURGICAL review: yes before closure
