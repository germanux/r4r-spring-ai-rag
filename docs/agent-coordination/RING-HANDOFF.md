# Backend ↔ Frontend Handoff

## Queue status snapshot

- **Backend (PC):** active `task-07-populate-production-rag`; technical gate evidence is green but closure metadata is incomplete.
- **Frontend (LP):** active `task-fe-03d-dom-state-tests`; deterministic gate remains failing with an explicit one-file correction packet.

## Concurrency and ownership check

- **PC write scope:** `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`
- **LP write scope:** `frontend/**`, `docs/frontend/**`
- **Overlap risk:** none (backend and frontend remain disjoint).

## Coordinated next actions

1. **PC (Level 2, task-07-populate-production-rag):** perform one closure-only pass and return complete request metadata + non-zero `vector_store` proof.
2. **LP (Level 1, task-fe-03d-dom-state-tests):** perform one bounded spec correction exactly per Codex packet and rerun FE-03D gate.

## Exact gates

- **PC:** `git diff --check` then full task-07 gate command from `.opencode/task-plan.backend.json`.
- **LP:** `git diff --check` then `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`.

## Integration risks to monitor next cycle

- Repeated PC non-acceptance if closure metadata remains null despite gate-green output.
- Repeated LP gate churn if final patch and diagnostic artifacts diverge from packet constraints.
