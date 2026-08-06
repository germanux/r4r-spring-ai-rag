# Backend ↔ Frontend handoff — run 20260806T010642Z

## Queue state summary

- **Backend (PC):** `task-06f-ingestion-validation` is gate-green but pending mandatory SURGICAL closure decision.
- **Frontend (LP):** `task-fe-03c-citations` has an active unaccepted spec diff under Codex `REVISE` instructions.

## Ownership and write-scope separation

### Backend package in scope now

- **Level:** 3 review pass (SURGICAL)
- **Role:** SURGICAL Codex reviewer
- **Task ID:** `task-06f-ingestion-validation` / `BE-06F-A`
- **Dependencies:** `task-06e-child-process:ACCEPTED`
- **allowed_paths:** `src/test/resources/application.yml`, `.opencode/current/PC/**`
- **Exact gate:** `./scripts/task-gate.sh task-06f-ingestion-validation`
- **Required review:** explicit SURGICAL `ACCEPT` or `REVISE`

### Frontend package in scope now

- **Level:** 1
- **Role:** LP
- **Task ID:** `task-fe-03c-citations` / `FE-03C-A`
- **Dependencies:** `task-fe-03b-answer-abstention:ACCEPTED`
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate:** `./scripts/frontend-task-gate.sh task-fe-03c-citations`
- **Required review:** SURGICAL `ACCEPT` before closure

## Integration-risk notes

1. Advancing backend to `task-07-*` before SURGICAL acceptance of `task-06f-*` would violate dependency sequencing.
2. Frontend FE-03C can report green while still missing contract-specific DOM assertions unless Codex-required checks are explicitly verified.
3. Keep backend and frontend edits disjoint during this cycle; no cross-queue write expansion is justified by current evidence.

## Immediate coordination directive

- Hold backend implementation edits; execute SURGICAL review decision only.
- Continue LP FE-03C-A correction pass and re-gate once.
- Require SURGICAL review for both queues before any closure claim.
