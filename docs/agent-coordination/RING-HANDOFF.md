# Backend ↔ Frontend handoff

## Coordination status

- **Backend (PC):** gate-green but pending Codex acceptance on `task-06e-child-process`.
- **Frontend (LP):** Codex REVISE unresolved on `task-fe-01-angular17-bootstrap`, with likely in-progress `frontend/angular.json` correction not yet acceptance-proven in this snapshot.

## Cross-stack dependencies and risk

1. **No backend API contract change is currently authorized.** Backend must stay bounded to task-06e test/process behavior.
2. **Frontend environment selection is release-critical.** If production replacement is wrong, FE may target localhost in production builds.
3. **Do not advance cross-stack features** (RAG client/UI integration) until FE-01 and BE-06e are each Codex-accepted.

## Bounded next actions

### PC (backend)
- Review current gate-green snapshot against mandatory Codex packet, then obtain Codex decision for task closure.

### LP (frontend)
- Validate `frontend/angular.json` production replacement fix through exact FE-01 gate and provide requirement-to-file traceability.

## Acceptance gates to unblock integration

- `./scripts/task-gate.sh task-06e-child-process` => `exit 0` and Codex `ACCEPT`.
- `./scripts/frontend-task-gate.sh task-fe-01-angular17-bootstrap` => `exit 0` and Codex `ACCEPT`.

Only after both are accepted should controllers schedule subsequent cross-stack tasks.
