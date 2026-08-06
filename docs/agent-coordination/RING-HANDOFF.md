# Backend ↔ Frontend handoff

## Queue status

- **Backend (PC parent task `task-07-populate-production-rag`):** gate-green evidence exists, but closure is blocked by `CHECKPOINT_COMMIT_FAILED` and pending Codex decision (`codex_decision=null`).
- **Frontend (LP parent task `task-fe-03d-dom-state-tests`):** deterministic gate is red (exit `2`) with an active Codex `REVISE` packet.

## Coordination decision

1. **Backend:** hold new PC implementation work; route an immediate **Level 3 SURGICAL review-only** pass for task-07 checkpoint evidence and commit-failure classification.
2. **Frontend:** continue **Level 1 LP** bounded correction in one spec file exactly per Codex packet, then rerun guard + exact gate once.

## Ownership and scope separation

- Backend path ownership (current PC evidence): `docs/backend/**`, `src/main/**`, `src/test/**`.
- Frontend path ownership (current LP evidence): `frontend/src/app/features/rag/rag-page.component.spec.ts`.
- No current write-scope overlap is required between PC and LP passes.

## Proposed action records

### Action A (backend)
- **Level:** 3
- **Role:** SURGICAL
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:** existing gate-green checkpoint request and controller failure evidence
- **allowed_paths:** backend task scope only for product fixes; otherwise operational surgical package if controller-level defect
- **Exact gate:** task-07 gate command from `.opencode/task-plan.backend.json`
- **Mandatory review:** SURGICAL is the actor and final reviewer for closure under hierarchy policy

### Action B (frontend)
- **Level:** 1
- **Role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** Codex REVISE packet and accepted prior frontend tasks
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts` (bounded execution scope)
- **Exact gate:** `git diff --check` then `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
- **Mandatory review:** SURGICAL review required after LP gate-green result

## Integration risk watchlist

- Backend acceptance cannot progress until checkpoint commit failure is classified/resolved.
- Frontend may continue to churn if LP deviates from already-prescribed corrections.
