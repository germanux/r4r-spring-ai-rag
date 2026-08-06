# Backend / Frontend Handoff — RUN 20260806T191631Z

## Queue status snapshot
- **Backend (PC):** `task-07-populate-production-rag` is gate-green but closure-pending SURGICAL review.
- **Frontend (LP):** `task-fe-03d-dom-state-tests` is gate-red and requires one bounded spec correction.

## Ownership and write-scope separation
- **PC queue (backend):** keep ownership on backend task artifacts only (`pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**` per task plan).
- **LP queue (frontend):** for this pass, restrict edits to `frontend/src/app/features/rag/rag-page.component.spec.ts`.
- No cross-queue overlapping write scope is requested in this cycle.

## Directed next actions

### 1) Backend closure action
- **Implementation level:** Level 3
- **Assigned role:** SURGICAL Codex (review-only)
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:** Existing PC gate-green checkpoint request in `worker-requests/PC.json`
- **allowed_paths:** `[]` (review-only)
- **Exact gate:** preserve satisfaction of task-07 gate already achieved; apply hierarchy closure requirements
- **Required SURGICAL review:** yes (this is the action)

### 2) Frontend correction action
- **Implementation level:** Level 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** `task-fe-03c-citations: ACCEPTED`
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate:** `git diff --check` then `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
- **Required SURGICAL review:** yes, after LP gate result

## Integration risks to monitor
1. Backend queue churn risk if PC resumes coding before SURGICAL verdict on current checkpoint.
2. Frontend repeated-attempt risk if LP broadens scope beyond the prescribed single spec file.
3. Evidence consistency risk if LP submits diagnostics not aligned to the final gate execution.
