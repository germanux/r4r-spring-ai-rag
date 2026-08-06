# PC code review (backend)

## Evidence reviewed

- `runtime/ring-agent/ring/20260806T193633Z/worker-requests/PC.json`
- `runtime/ring-agent/ring/20260806T193633Z/pc-runtime/progress.json`
- `runtime/ring-agent/ring/20260806T193633Z/pc-runtime/gate_summary.md`
- `runtime/ring-agent/ring/20260806T193633Z/pc-runtime/previous-ring-qwen3-directive.json`
- `runtime/ring-agent/ring/20260806T193633Z/pc-git-status.txt`

## First current defect

The current defect is **workflow-state, not demonstrated code breakage**: PC has already produced a gate-green checkpoint request for `task-07-populate-production-rag` (`gate_exit=0`), but `codex_decision` is still `null`, and task status remains `BLOCKED` in progress evidence. Under hierarchy policy, task closure is impossible before SURGICAL review returns an explicit decision.

## Bounded next action package

- **Implementation level:** 3 (SURGICAL review lane)
- **Assigned role:** SURGICAL Codex (`r4r-surgical-architect` / `r4r-surgical-fixer`)
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:**
  - Existing PC checkpoint request with green gate evidence (`worker-requests/PC.json`)
  - Closure policy from `.opencode/task-plan.hierarchy.json`
- **allowed_paths:** read-only review of current evidence packet; no new implementation paths until review outcome
- **Exact gate / constraint:**
  - Keep the canonical task-07 deterministic gate defined in `.opencode/task-plan.backend.json` as the authoritative implementation gate.
  - Enforce closure sequence: `exact-gate-green + scope-clean + surgical-accept + controller-commit`.
- **Required SURGICAL review:** **mandatory now**; this pass must emit `ACCEPT` or `REVISE` before any further PC coding cycle.

## Explicit acceptance conditions

1. SURGICAL returns explicit disposition (`ACCEPT` or `REVISE`) for the current checkpoint evidence.
2. If `REVISE`, next PC pass is one bounded correction tied to first evidenced defect only.
3. If `ACCEPT`, controller can proceed with checkpoint/closure flow for the active backend task.

## Avoid repeating

- Do not run another backend edit+gate cycle while this same gate-green request is still unresolved (`codex_decision=null`).
