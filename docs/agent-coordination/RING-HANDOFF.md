# Backend ↔ Frontend handoff

## Concurrency and scope

- **PC lane (backend):** `task-07-populate-production-rag`, backend paths (`pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`).
- **LP lane (frontend):** `task-fe-03d-dom-state-tests`, single frontend spec path (`frontend/src/app/features/rag/rag-page.component.spec.ts`).
- **Scope overlap check:** none observed from current directives/evidence, so parallel progress is safe.

## Dispatch decisions

### Package A
- **Implementation level:** Level 2
- **Assigned role:** PC
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:** backend task chain through accepted `task-06f-ingestion-validation`
- **allowed_paths:** `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`
- **Exact gate:** `git diff --check` then task-07 backend composite gate command
- **Handoff objective:** convert gate-green checkpoint state into closure-complete acceptance evidence (including non-zero `vector_store` proof).

### Package B
- **Implementation level:** Level 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** accepted `task-fe-03c-citations`
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate:** `git diff --check` then `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
- **Handoff objective:** finish one-file DOM-state test correction per Codex packet and re-establish deterministic gate green.

## Integration risks to monitor next cycle

1. Backend task-07 can stall again if proof artifacts omit row-count/exit details despite green gate.
2. Frontend FE-03D can fail early on formatting/structure errors before runtime assertions execute.
