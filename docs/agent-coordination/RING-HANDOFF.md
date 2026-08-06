# Backend ↔ Frontend handoff — run 20260806T171220Z

## Queue state summary

- **Backend (PC):** `task-07-populate-production-rag` is active but must remain **held** due to prerequisite sequencing (`BE-07-A` not accepted).
- **Frontend (LP):** `task-fe-03d-dom-state-tests` has a gate-green checkpoint and is awaiting **SURGICAL review decision**.

## Disjoint ownership and dependency guard

1. Keep current LP review flow frontend-only until Codex returns `ACCEPT`/`REVISE`.
2. Keep PC backend implementation paused; do not consume compute on blocked task-07 gate loops.
3. Do not overlap writable scopes across queues while dependency state is unresolved.

## Action packages

### Package A (immediate)
- **Level:** 1
- **Role:** LP + SURGICAL reviewer
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** Gate green evidence already produced
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts` (if revision required)
- **Exact gate:** `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
- **Required SURGICAL review:** Yes (mandatory before closure)

### Package B (immediate control)
- **Level:** 2 control action
- **Role:** PC
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:** `BE-07-A:ACCEPTED` required before `BE-07-B`
- **allowed_paths:** unchanged prior backend task scope; **no new edits authorized while held**
- **Exact gate:** deferred until unblocked; then run exact task-07 gate from `.opencode/task-plan.backend.json`
- **Required SURGICAL review:** Yes after any later gate-green pass

## Evidence anchors

- `runtime/ring-agent/ring/20260806T171220Z/pc-runtime/previous-ring-qwen3-directive.json`
- `runtime/ring-agent/ring/20260806T171220Z/pc-runtime/progress.json`
- `runtime/ring-agent/ring/20260806T171220Z/pc-runtime/gate_summary.md`
- `runtime/ring-agent/ring/20260806T171220Z/worker-request-manifest.json`
- `runtime/ring-agent/ring/20260806T171220Z/lp-runtime/checkpoint.json`
