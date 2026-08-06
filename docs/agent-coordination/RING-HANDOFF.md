# Backend ↔ Frontend handoff (cycle 20260806T193132Z)

## Ownership split (kept disjoint)
- **Backend queue:** PC task `task-07-populate-production-rag`.
- **Frontend queue:** LP task `task-fe-03d-dom-state-tests`.
- **Write-scope overlap check:** none in this cycle (backend `src/main|src/test|docs/backend` vs frontend spec under `frontend/...`).

## Backend handoff
- **Implementation level:** Level 3 (review-only)
- **Assigned role:** SURGICAL Codex
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:** existing gate-green checkpoint request already emitted by PC
- **allowed_paths:** read-only review (`[]` for this pass)
- **Exact gate/constraints:** preserve backend task-07 gate contract and hierarchy closure policy
- **Action:** return explicit `ACCEPT` or `REVISE` before any new PC edit pass

## Frontend handoff
- **Implementation level:** Level 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** codex revise packet + prior accepted FE-03C baseline
- **allowed_paths (task):** `frontend/**`, `docs/frontend/**`
- **allowed_paths (pass constraint):** `frontend/src/app/features/rag/rag-page.component.spec.ts` only
- **Exact gate:** `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests` (after `git diff --check`)
- **Action:** one corrective pass replacing defective tests with the three prescribed DOM-state tests

## Integration risks to monitor
1. PC queue can deadlock if SURGICAL review is not executed promptly after gate-green evidence.
2. LP has reached global attempt limit in prior run; any scope drift likely causes another failed iteration.
3. Cross-queue contamination risk if LP edits beyond single spec file or if PC resumes coding before review verdict.

## Closure rule reminder
Both backend and frontend tasks require SURGICAL review (`ACCEPT`) after exact gate success; no queue self-closes.
