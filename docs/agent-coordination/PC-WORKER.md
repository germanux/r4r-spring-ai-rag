# PC code review (backend queue)

## Evidence reviewed

- `runtime/ring-agent/ring/20260806T000832Z/pc-runtime/progress.json`
- `runtime/ring-agent/ring/20260806T000832Z/pc-runtime/gate_summary.md`
- `runtime/ring-agent/ring/20260806T000832Z/pc-runtime/checkpoint.json`
- `runtime/ring-agent/ring/20260806T000832Z/worker-requests/PC.json`
- `runtime/ring-agent/ring/20260806T000832Z/pc-git-status.txt`
- `runtime/ring-agent/ring/20260806T000832Z/pc-git-diff-stat.txt`

## First current defect

`task-06f-ingestion-validation` is not blocked by a failing gate; it is blocked by missing closure evidence.

- Gate evidence is green (`exit 0`) on attempt 1.
- Checkpoint status is `no-product-diff`.
- Worker request is `gate-green-no-checkpoint` with `codex_decision: null`.

So the immediate defect is **review-state incompleteness** (no SURGICAL ACCEPT/REVISE recorded), not backend implementation failure.

## Bounded next action package

- **Implementation level:** 3
- **Assigned role:** SURGICAL Codex reviewer (OpenCode)
- **Task ID:** `task-06f-ingestion-validation` (review pass over BE-06F state)
- **Dependencies:** `task-06e-child-process:ACCEPTED` (already satisfied)
- **allowed_paths (for any subsequent PC revise pass only):**
  - `src/test/resources/application.yml`
  - `.opencode/current/PC/**`
- **Exact gate constraint:** `./scripts/task-gate.sh task-06f-ingestion-validation` must remain green (`exit 0`)
- **Required SURGICAL review:** mandatory before closure per `.opencode/task-plan.hierarchy.json` (`review_policy.closure_requires` includes `surgical-accept`)

### One-pass instruction

Run one SURGICAL review on the already gate-green package. If SURGICAL returns `ACCEPT`, close task-06f. If SURGICAL returns `REVISE`, dispatch exactly one bounded PC correction pass under BE-06F-A scope, then rerun the exact gate once.

## Avoid repeating

- Do **not** rerun unchanged backend gates while Codex decision is pending.
- Do **not** widen scope into BE-06F-B or unrelated backend code without a new first failure.
