# Backend ↔ Frontend handoff — run 20260806T192632Z

## Queue status

- **Backend queue (PC task):** hold additional implementation; run the pending SURGICAL review decision for the existing gate-green checkpoint on `task-07-populate-production-rag`.
- **Frontend queue (LP task):** continue one bounded correction pass on `task-fe-03d-dom-state-tests`.

Current evidence shows no backend/frontend write-scope overlap for the proposed next pass.

## Backend package

- **Implementation level:** 3
- **Assigned role:** SURGICAL
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:** gate-green request exists with `codex_decision=null`.
- **allowed_paths:** `[]` (read-only review pass)
- **Exact gate:** preserve task-07 gate satisfaction from `.opencode/task-plan.backend.json`.
- **Required SURGICAL review:** mandatory (this package is the review).
- **Acceptance condition:** review outcome recorded (`ACCEPT` or `REVISE`) for the pending checkpoint request.

## Frontend package

- **Implementation level:** 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** `task-fe-03c-citations:ACCEPTED`; correction rules in LP memory.
- **allowed_paths:**
  - Canonical: `frontend/**`, `docs/frontend/**`
  - This pass constrained to: `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate:** `git diff --check` then `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`.
- **Required SURGICAL review:** mandatory after LP gate-green.
- **Acceptance condition:** `exact-gate-green + scope-clean + surgical-accept + controller-commit`.

## Integration risks for next cycle

1. PC queue churn if coding resumes before the pending SURGICAL decision lands.
2. LP repeated red-gate risk if corrections deviate from the prescribed Subject-driven DOM assertions.
3. Backend task-07 reproducibility risk if `.env`/Docker DB state drifts between attempts.
