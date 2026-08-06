# Backend ↔ Frontend handoff and queue isolation

## Current queue status

- **Backend (PC active task):** `task-07-populate-production-rag`
  - Current state is a gate-green checkpoint request awaiting SURGICAL disposition.
- **Frontend (LP active task):** `task-fe-03d-dom-state-tests`
  - Current state is deterministic red gate with a bounded Codex REVISE packet.

## Ownership and write-scope separation for next pass

### Package A (Backend review hold)
- **Level:** 3
- **Owner:** SURGICAL
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:** existing PC checkpoint evidence only
- **allowed_paths:** review-only (`[]`)
- **Exact gate/constraint:** hierarchy closure policy (`exact-gate-green + scope-clean + surgical-accept + controller-commit`)

### Package B (Frontend correction)
- **Level:** 1
- **Owner:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** active Codex REVISE correction packet
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts` (single-file bounded pass)
- **Exact gate:** `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`

## Integration risk controls

1. **No overlapping write scopes:** backend review pass is read-only; frontend correction is single spec file.
2. **No backend implementation churn before review:** PC stays held for implementation until SURGICAL emits ACCEPT/REVISE.
3. **No phase crossover:** LP does not write backend docs/code in this cycle despite hierarchy BE-07 LP sub-packages existing historically.

## Required SURGICAL checkpoints

- SURGICAL must review LP output before FE-03D closure.
- SURGICAL must disposition the existing PC checkpoint before any additional PC coding pass.
