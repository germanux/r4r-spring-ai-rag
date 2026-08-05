# Backend ↔ Frontend handoff (Ring)

## Queue separation status

Ownership remains disjoint for this cycle and can run concurrently:

- **PC/backend scope:** review posture on `task-06f-ingestion-validation` (no new product edit required unless Codex REVISE).
- **LP/frontend scope:** active FE-03C test-only correction in `rag-page.component.spec.ts`.

No overlapping `allowed_paths` are required by the current next actions.

## Active packages

### Backend package
- **Level / role:** Level 2 / PC
- **Task ID:** `task-06f-ingestion-validation` (`BE-06F-A` review stage)
- **Dependencies:** `task-06e-child-process:ACCEPTED`
- **allowed_paths:** `src/test/resources/application.yml`, `.opencode/current/PC/**` (only if REVISE)
- **Exact gate:** `./scripts/task-gate.sh task-06f-ingestion-validation`
- **SURGICAL review:** required before closure

### Frontend package
- **Level / role:** Level 1 / LP
- **Task ID:** `task-fe-03c-citations` (`FE-03C-A`)
- **Dependencies:** `task-fe-03b-answer-abstention:ACCEPTED`
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate:** `./scripts/frontend-task-gate.sh task-fe-03c-citations`
- **SURGICAL review:** required before closure

## Integration risks to monitor

1. **LP evidence consistency risk:** `lp-runtime/memory.md` says gate not run, while `lp-runtime/gate_summary.md` is green. Treat closure evidence as incomplete until one coherent run packet is produced.
2. **Scope creep risk in LP spec edit:** current diff size suggests potential drift into FE-03D behaviors; keep FE-03C assertions only.
3. **Unnecessary churn risk on backend:** PC should avoid fresh edits while Codex decision is pending on a green gate state.

## Handoff decision

- Backend: **hold edits, prioritize SURGICAL review of current evidence**.
- Frontend: **continue one bounded LP pass to satisfy Codex REVISE and regenerate exact-gate evidence**.
