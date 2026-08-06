# Backend ↔ Frontend handoff

## Queue split for this cycle
- **Backend (PC): HOLD**
  - Block reason: task-07 backend execution is still gated by unmet `BE-07-A` acceptance.
  - Current backend edits exist, but no new unblock evidence is present in this run.
- **Frontend (LP): CONTINUE**
  - Execute one bounded revise pass for `task-fe-03d-dom-state-tests` on the owned spec file.

## Ownership and overlap check
- PC current changed paths: `src/main/**`, `src/test/**` (backend layer).
- LP current changed path: `frontend/src/app/features/rag/rag-page.component.spec.ts` (frontend layer).
- **Result:** no active write-scope overlap in this run snapshot.

## Proposed bounded actions

### Action A — backend hold
- **Implementation level:** 2
- **Assigned role:** PC
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:** `BE-07-A:ACCEPTED`
- **allowed_paths:** none during hold pass (future canonical package scope: `src/**`, `docs/backend/**`)
- **Exact gate:** deferred while blocked; resume only after dependency acceptance evidence
- **Required SURGICAL review:** required before any closure when backend work resumes

### Action B — frontend revise
- **Implementation level:** 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** `task-fe-03c-citations:ACCEPTED` (satisfied)
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate:** `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
- **Required SURGICAL review:** Codex `ACCEPT` required before closure

## Integration-risk notes
1. If backend is resumed before dependency acceptance, PC can accumulate non-actionable churn and stale failures.
2. Repeated LP revise loops without strict checklist closure can stall frontend phase progression.
3. Existing unreviewed backend diffs should not be treated as accepted progress absent SURGICAL Codex `ACCEPT` evidence.
