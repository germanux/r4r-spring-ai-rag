# Backend ↔ Frontend handoff and queue isolation

## Ownership split for this cycle

- **Backend (PC): HOLD** on `task-07-populate-production-rag` until dependency unblock evidence exists.
- **Frontend (LP): CONTINUE** `task-fe-03d-dom-state-tests` with one bounded spec-file revise pass.

## Dependency and risk notes

1. Backend dependency order is the controlling constraint:
   - BE-07-B style execution is blocked until `BE-07-A:ACCEPTED` is evidenced.
2. Frontend has a direct correction packet and can proceed independently.
3. Running both as implementation work now would be wasteful because backend is dependency-blocked while frontend has a concrete, local defect to fix.

## Write-scope disjointness

- **PC hold pass allowed_paths:** none (no implementation edits this cycle).
- **LP allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts` only.

No path overlap is introduced in this cycle.

## Required gates and reviews

- **PC:** no gate execution during hold pass; resume only after dependency acceptance evidence. Any resumed implementation still requires exact gate + SURGICAL `ACCEPT`.
- **LP:** `git diff --check` then `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`; closure requires SURGICAL `ACCEPT`.

## Evidence anchors

- `/home/german/Desarrollo/r4r-ring-agent.git/runtime/ring-agent/ring/20260806T160956Z/pc-runtime/previous-ring-qwen3-directive.json`
- `/home/german/Desarrollo/r4r-ring-agent.git/runtime/ring-agent/ring/20260806T160956Z/pc-runtime/progress.json`
- `/home/german/Desarrollo/r4r-ring-agent.git/runtime/ring-agent/ring/20260806T160956Z/lp-runtime/codex-qwen3-extra-instructions.md`
- `/home/german/Desarrollo/r4r-ring-agent.git/runtime/ring-agent/ring/20260806T160956Z/worker-request-manifest.json`
