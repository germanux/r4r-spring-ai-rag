# Backend ↔ Frontend handoff (Ring)

## Queue separation decision

- **Backend (PC task-07):** hold implementation; route to **SURGICAL review-only** because gate is already green but Codex disposition is missing.
- **Frontend (LP task-fe-03d):** continue one bounded spec-file correction pass per Codex `REVISE` packet.

This keeps backend/frontend ownership disjoint and avoids overlapping write scopes.

## Active bounded work packages

### 1) Backend closure review package

- **Implementation level:** Level 3
- **Assigned role:** SURGICAL
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:** Existing gate-green checkpoint request (`worker-requests/PC.json`).
- **allowed_paths:** `[]` (read-only review pass)
- **Exact gate:** Reuse previously recorded exact gate evidence for task-07; no new PC gate run unless `REVISE`.
- **Required SURGICAL review:** This package itself is the mandatory review.
- **Acceptance evidence:** Explicit Codex `ACCEPT`/`REVISE` attached to the current checkpoint evidence.

### 2) Frontend correction package

- **Implementation level:** Level 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** Active Codex `REVISE` instructions and red gate diagnostics.
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts` (stricter-than-plan correction scope).
- **Exact gate:**
  1. `git diff --check`
  2. `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
- **Required SURGICAL review:** Mandatory post-gate `ACCEPT` before closure.
- **Acceptance evidence:** Gate exit 0 + consistent diagnostics + Codex `ACCEPT`.

## Integration risks to monitor next cycle

1. **Backend closure risk:** if PC is re-dispatched before SURGICAL disposition, existing green evidence may churn.
2. **Frontend repeat-failure risk:** LP may fail again if selector-level assertions are not implemented exactly as directed.
3. **Evidence quality risk:** LP understanding artifacts were previously inadequate; next run must map requirements to selectors/assertions explicitly.

## Evidence basis

- `runtime/ring-agent/ring/20260806T190129Z/worker-requests/PC.json`
- `runtime/ring-agent/ring/20260806T190129Z/pc-runtime/progress.json`
- `runtime/ring-agent/ring/20260806T190129Z/lp-runtime/memory.md`
- `runtime/ring-agent/ring/20260806T190129Z/lp-runtime/codex-qwen3-extra-instructions.md`
