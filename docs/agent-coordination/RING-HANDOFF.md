# Backend ↔ Frontend handoff

## Queue status
- **Backend (PC):** `task-06f-ingestion-validation` is gate-green but pending mandatory SURGICAL decision.
- **Frontend (LP):** `task-fe-03c-citations` is in REVISE state with an active spec diff and missing closure evidence.

## Ownership and write-scope separation
- Backend correction lane remains under `BE-06F-A` (`src/test/resources/application.yml`, `.opencode/current/PC/**`).
- Frontend correction lane remains under `FE-03C-A` (`frontend/src/app/features/rag/rag-page.component.spec.ts`).
- No cross-queue write overlap is authorized in this cycle.

## Dependencies and sequencing
1. **PC lane:** obtain SURGICAL review decision first (`ACCEPT`/`REVISE`) on existing gate-green package.
2. **LP lane:** complete FE-03C-A assertions + gate + SURGICAL review.
3. Only after each lane records SURGICAL `ACCEPT` may downstream tasks (`task-07` backend, `task-fe-03d` frontend) be considered.

## Action package contract (explicit)
- **BE-06F-A**
  - Implementation level: **2**
  - Assigned role: **PC**
  - Task ID: `task-06f-ingestion-validation`
  - Dependencies: `task-06e-child-process:ACCEPTED`
  - `allowed_paths`: `src/test/resources/application.yml`, `.opencode/current/PC/**`
  - Exact gate: `./scripts/task-gate.sh task-06f-ingestion-validation`
  - Required SURGICAL review: **mandatory ACCEPT before closure**

- **FE-03C-A**
  - Implementation level: **1**
  - Assigned role: **LP**
  - Task ID: `task-fe-03c-citations`
  - Dependencies: `task-fe-03b-answer-abstention:ACCEPTED`
  - `allowed_paths`: `frontend/src/app/features/rag/rag-page.component.spec.ts`
  - Exact gate: `./scripts/frontend-task-gate.sh task-fe-03c-citations`
  - Required SURGICAL review: **mandatory ACCEPT before closure**

## Integration risks to hold
- False progress risk if either queue advances without explicit SURGICAL acceptance.
- Frontend artifact hygiene risk from untracked `r4r-gemma4-lp.patch`; keep FE-03C correction strictly bounded to allowed path and gate evidence.

## Required validation gates
- Backend exact gate: `./scripts/task-gate.sh task-06f-ingestion-validation`
- Frontend exact gate: `./scripts/frontend-task-gate.sh task-fe-03c-citations`
- Both lanes: mandatory SURGICAL Codex `ACCEPT` per `.opencode/task-plan.hierarchy.json` review policy.
