# Backend ↔ Frontend handoff

## Queue separation decision
- **Backend (PC): HOLD** on `task-07-populate-production-rag` until hierarchy dependency `BE-07-A:ACCEPTED` exists.
- **Frontend (LP): REVIEW** on `task-fe-03c-citations` checkpoint; no new implementation dispatch until SURGICAL decision.

This keeps ownership disjoint and avoids overlapping write scopes.

## Active work packages

### 1) Backend hold package
- **Level:** 2 (PC)
- **Role:** PC
- **Task ID:** `task-07-populate-production-rag` (blocked behind `BE-07-A`)
- **Dependencies:** `BE-07-A:ACCEPTED`, backend-phase-active
- **allowed_paths:** `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`
- **Exact gate:** task-07 gate command in `.opencode/task-plan.backend.json`
- **SURGICAL requirement:** mandatory review/accept on completion

### 2) Frontend review package
- **Level:** 1 (LP)
- **Role:** LP
- **Task ID:** `task-fe-03c-citations` (`FE-03C-A`)
- **Dependencies:** satisfied for current checkpoint; closure pending review
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate:** `./scripts/frontend-task-gate.sh task-fe-03c-citations` (green evidence present)
- **SURGICAL requirement:** mandatory `ACCEPT` before progressing to FE-03D

## Integration risks to watch
1. Premature backend resume can waste cycles on task-07 while dependency state is still invalid.
2. Frontend may carry incomplete FE-03C acceptance if FE-03D starts before Codex confirms assertion coverage.

## Next coordination checkpoint
Proceed when either:
- BE-07-A acceptance evidence is published (then reopen PC with one bounded pass), or
- SURGICAL Codex returns FE-03C decision (ACCEPT/REVISE) for LP checkpoint.
