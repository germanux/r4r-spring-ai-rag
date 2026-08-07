# Backend ↔ Frontend Handoff

## Queue status

- **Backend (PC):** continue `task-07-populate-production-rag` closure-quality pass.
- **Frontend (LP):** continue `task-fe-03d-dom-state-tests` one-file correction pass.

## Ownership and scope separation

- PC active edits are backend paths (`src/main/**`, `src/test/**`, `docs/backend/**`) per `pc-git-status.txt`.
- LP active edit is frontend spec path (`frontend/src/app/features/rag/rag-page.component.spec.ts`) per `lp-git-status.txt`.
- **No current write-scope overlap** between PC and LP tasks.

## Required packages for next pass

### Package PC-07-CLOSE (Level 2, PC)
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:** `task-06f-ingestion-validation:ACCEPTED`
- **allowed_paths:** `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`
- **Gate:**
  1. `git diff --check`
  2. task-07 deterministic gate command from `.opencode/task-plan.backend.json`
- **Acceptance evidence:** gate exit `0`, vector_store rows `> 0`, non-null closure metadata, controller-commit-capable state.

### Package LP-FE03D-REPAIR (Level 1, LP)
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** `task-fe-03c-citations:ACCEPTED`
- **allowed_paths:** `frontend/**`, `docs/frontend/**` (bounded to one spec file this pass)
- **Gate:**
  1. `git diff --check`
  2. `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
- **Acceptance evidence:** one-file corrective diff consistent with Codex packet + gate exit `0`.

## Integration risk watch

1. Backend loop risk: repeated gate-green with failed checkpoint commit can stall closure.
2. Frontend churn risk: large one-file diff may keep reintroducing the same malformed test patterns.
