# Backend ↔ Frontend handoff — run 20260806T195634Z

## Queue status split

- **Backend (PC task-07):** hold implementation; move to **SURGICAL review-only closure pass** first.
- **Frontend (LP task-fe-03d):** continue **one bounded correction pass** in a single spec file.

## Ownership and write-scope disjointness

- Backend active scope (if implementation is required after review): `pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`.
- Frontend active scope: `frontend/src/app/features/rag/rag-page.component.spec.ts`.

No scope overlap is present in this cycle; keep queues disjoint.

## Cross-stack integration risk notes

1. Backend task-07 is blocked on closure evidence (`codex_decision` and checkpoint/commit trail), not on a newly demonstrated failing gate.
2. Frontend task-fe-03d remains red and should not be widened into production code changes; keep it test-only.

## Coordinated next actions

### Action A (Backend)
- **Level:** 3
- **Role:** SURGICAL
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:** existing gate-green request evidence
- **allowed_paths:** review-only initially; if fix needed, backend task plan scope only
- **Exact gate:** backend task-07 gate command from `.opencode/task-plan.backend.json`
- **Required review:** SURGICAL decision must be explicit (`ACCEPT` or `REVISE`)

### Action B (Frontend)
- **Level:** 1
- **Role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** Codex REVISE packet already present
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate:** `git diff --check` then `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
- **Required review:** SURGICAL Codex `ACCEPT` before closure
