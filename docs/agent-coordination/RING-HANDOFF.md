# Backend ↔ Frontend handoff (cycle 20260806T184628Z)

## Queue separation decision
- **Backend owner path (PC/SURGICAL review):** task `task-07-populate-production-rag` evidence disposition only.
- **Frontend owner path (LP):** task `task-fe-03d-dom-state-tests` spec correction only.
- Write scopes are disjoint in this cycle (backend `src/**` + `docs/backend/**` vs frontend spec file), so both lanes can progress concurrently without overlap.

## Backend package
- **Level:** 3 review package
- **Role:** SURGICAL Codex
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:** validate BE-07-A/BE-07-B sequencing before closure.
- **allowed_paths (if revise implementation is required):** `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`.
- **Exact gate constraint:** task-07 backend gate command from `.opencode/task-plan.backend.json`.
- **Required SURGICAL review:** yes (this is the action itself).

## Frontend package
- **Level:** 1 implementation package
- **Role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** `task-fe-03c-citations:ACCEPTED`.
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts`.
- **Exact gate constraint:** `git diff --check` then `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`.
- **Required SURGICAL review:** yes, after gate-green.

## Integration risks to watch
1. **Backend dependency-order risk:** hierarchy marks BE-07-B dependent on BE-07-A acceptance; any mismatch must be resolved during SURGICAL disposition.
2. **Frontend evidence-consistency risk:** previous LP attempts mixed stale/insufficient diagnostics; next packet must align manifest, gate log, and task-gate result from one execution.

## Handoff readiness
- Frontend can execute immediately with bounded correction.
- Backend should not open a new coding loop until SURGICAL review outcome on the existing task-07 checkpoint.
