# PC code review (run 20260806T185129Z)

## Current evidence snapshot

- Active task: `task-07-populate-production-rag` (`pc-runtime/progress.json`).
- Deterministic gate summary is green (`exit 0`) (`pc-runtime/gate_summary.md`).
- Controller captured a gate-green checkpoint request with changed backend paths (`worker-requests/PC.json`).
- Task is still `BLOCKED` and there is no SURGICAL disposition yet (`codex_decision: null`) (`worker-requests/PC.json`, `pc-runtime/progress.json`).

## First current defect (PC queue)

The first defect is **not a new backend implementation failure**; it is a **missing mandatory SURGICAL review decision** for the already produced gate-green diff. Closing or re-running implementation now would bypass the required acceptance chain.

## Bounded next package

### Package ID: SURG-BE-07-REVIEW-01
- **Implementation level:** 3
- **Assigned role:** SURGICAL (`r4r-surgical-architect` / `r4r-surgical-fixer`)
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:**
  - Existing gate-green checkpoint evidence from PC attempt 1 (`worker-requests/PC.json`).
  - Closure policy in `.opencode/task-plan.hierarchy.json`.
- **allowed_paths:** `[]` (review-only pass; no product writes)
- **Exact gate:** Review-only disposition against the already-executed exact gate for `task-07-populate-production-rag`.
- **Required SURGICAL review:** Yes (this package is the SURGICAL review itself).

### Acceptance evidence required from this pass
1. Explicit SURGICAL `ACCEPT` or `REVISE` attached to the current checkpoint diff.
2. If `REVISE`, one bounded correction target naming exact files and one exact rerun gate.
3. Confirmation that closure policy remains: `exact-gate-green + scope-clean + surgical-accept + controller-commit`.

## Follow-on only if SURGICAL returns REVISE

### Package ID: BE-07-B-PC-REVISE-01 (conditional)
- **Implementation level:** 2
- **Assigned role:** PC
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:** `SURG-BE-07-REVIEW-01:REVISE`
- **allowed_paths:** `src/**`, `docs/backend/**` (from hierarchy BE-07-B) and must remain within task plan allowed paths.
- **Exact gate:** backend task-07 gate from `.opencode/task-plan.backend.json`.
- **Required SURGICAL review:** Mandatory before closure (per hierarchy policy).

## Do-not-repeat guard

- Do **not** run another full PC implementation loop before SURGICAL disposition of current evidence.
- Do **not** claim acceptance from gate-green checkpoint alone.
