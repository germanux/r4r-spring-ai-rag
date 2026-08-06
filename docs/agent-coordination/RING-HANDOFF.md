# Backend ↔ Frontend handoff — run 20260806T172722Z

## Queue state snapshot
- **Backend (PC active task):** `task-07-populate-production-rag` is blocked/held.
- **Frontend (LP active task):** `task-fe-03d-dom-state-tests` continues with Codex `REVISE` corrections.

## Ownership separation for next pass

### Backend lane
- **Implementation level:** Level 3 review pass (SURGICAL), with PC on HOLD.
- **Assigned role:** SURGICAL reviewer.
- **Task ID:** `task-07-populate-production-rag`.
- **Dependencies:** `BE-07-B` cannot proceed until `BE-07-A:ACCEPTED`.
- **allowed_paths (for future PC implementation when unblocked):** backend task scope from plan (`pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`).
- **Exact gate (when unblocked):** task-07 backend command from `.opencode/task-plan.backend.json`.
- **Required SURGICAL review:** immediate review disposition over current red-gate + dirty-diff evidence; later mandatory `ACCEPT` after gate-green run.

### Frontend lane
- **Implementation level:** Level 1.
- **Assigned role:** LP.
- **Task ID:** `task-fe-03d-dom-state-tests` (`FE-03D-A`).
- **Dependencies:** satisfied (`task-fe-03c-citations:ACCEPTED`).
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts` only.
- **Exact gate:** `git diff --check` then `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`.
- **Required SURGICAL review:** Codex `ACCEPT` required before closure.

## Integration-risk controls
1. Keep PC paused to prevent additional backend churn before dependency release and surgical disposition.
2. Keep LP strictly single-file scoped to avoid frontend/backed scope bleed and preserve deterministic Codex reviewability.
3. Do not advance FE-03D-B / later frontend tasks until FE-03D-A is accepted.
