# Backend ↔ Frontend handoff

## Queue separation status

- **Backend (PC task-07):** closure-state review required; no new implementation pass authorized until SURGICAL decision is recorded.
- **Frontend (LP FE-03D):** bounded correction pass is authorized on one spec file per Codex REVISE.

Current scopes are disjoint (backend `src/**` + `docs/backend/**` vs frontend `frontend/**`), so no write-scope overlap is present in this cycle.

## Directed work packages

### Package A — backend closure classification
- **Level:** 3
- **Role:** SURGICAL
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:** gate-green evidence from PC attempt 1
- **allowed_paths:** review-only (no product edits requested in this pass)
- **Exact gate/constraint:** hierarchy closure chain (`exact-gate-green + scope-clean + surgical-accept + controller-commit`)
- **Required SURGICAL review:** this package is the SURGICAL review step.

### Package B — frontend FE-03D correction
- **Level:** 1
- **Role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** active Codex REVISE packet
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate:** `git diff --check` then `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
- **Required SURGICAL review:** mandatory after LP gate result for closure.

## Integration risks to watch

1. Backend churn risk if PC repeats gate runs without SURGICAL closure decision.
2. Frontend churn risk if LP broadens beyond the prescribed three-test correction.
3. Any cross-queue scope expansion must be held and re-routed to level-3 SURGICAL.
