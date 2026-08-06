# Backend ↔ Frontend handoff — run 20260806T192132Z

## Queue status

- **Backend (PC):** hold implementation; perform SURGICAL review-only decision on existing gate-green checkpoint for `task-07-populate-production-rag`.
- **Frontend (LP):** continue one bounded correction pass on `task-fe-03d-dom-state-tests`.

No backend/frontend write-scope overlap is present in current evidence.

## Backend package

- **Level:** 3 (review control)
- **Role:** SURGICAL
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:** gate-green checkpoint request exists; codex decision pending
- **allowed_paths:** `[]` (read-only review)
- **Exact gate:** preserve task-07 gate satisfaction already demonstrated
- **Required SURGICAL review:** this package is the mandatory SURGICAL pass itself
- **Acceptance condition:** explicit review outcome (`ACCEPT`/`REVISE`) recorded for the pending request

## Frontend package

- **Level:** 1
- **Role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** `task-fe-03c-citations:ACCEPTED`; codex revise constraints in LP memory
- **allowed_paths:**
  - Canonical: `frontend/**`, `docs/frontend/**`
  - This corrective pass: `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate:** `git diff --check` then `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
- **Required SURGICAL review:** mandatory after gate-green
- **Acceptance condition:** gate green + scope clean + SURGICAL `ACCEPT`

## Integration risks to watch next cycle

1. PC queue churn risk if implementation resumes before pending SURGICAL review resolves.
2. LP repeated red-gate risk if corrections drift away from prescribed Subject-based DOM tests.
3. Backend task-07 reproducibility risk if Docker/.env runtime state changes between attempts.
