# Backend ↔ Frontend handoff — run 20260807T012027Z

## Queue status

- **Backend (PC):** `task-07-populate-production-rag` continues.
- **Frontend (LP):** `task-fe-03d-dom-state-tests` continues.

## Ownership and scope separation

The two actions remain disjoint and can proceed in parallel:

- **PC allowed_paths:** `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`
- **LP allowed_paths (effective bounded package):** `frontend/src/app/features/rag/rag-page.component.spec.ts`

No path overlap is present between backend and frontend packages in this cycle.

## Bounded action packets

### Packet A
- **Implementation level:** Level 2
- **Assigned role:** PC
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:** `task-06f-ingestion-validation:ACCEPTED`
- **Exact gate:**
  - `git diff --check`
  - exact task-07 backend gate command from `.opencode/task-plan.backend.json`
  - closure policy `exact-gate-green + scope-clean + controller-commit`

### Packet B
- **Implementation level:** Level 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** `task-fe-03c-citations:ACCEPTED`
- **Exact gate:**
  - `git diff --check`
  - `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
  - closure policy `exact-gate-green + scope-clean + controller-commit`

## Integration risks to monitor

1. Backend closure can stall despite green gates if row-count proof and command-exit evidence remain incomplete.
2. Frontend can fail before semantic assertions if file-format/structure issues recur.

## Handoff decision

Proceed with both packets concurrently; hold neither queue because SURGICAL is disabled and no write-scope overlap exists.
