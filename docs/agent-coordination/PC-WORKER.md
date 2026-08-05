# PC code review (Ring)

## Current diagnosis

First current defect for the PC queue is **missing mandatory SURGICAL acceptance evidence**, not a new backend code failure.

Evidence:
- `worker-requests/PC.json` records `reason: gate-green-no-checkpoint` and `codex_decision: null`.
- `pc-runtime/gate_summary.md` is green (`exit code 0`).
- `pc-runtime/checkpoint.json` shows `status: no-product-diff` for `task-06f-ingestion-validation`.
- `pc-runtime/progress.json` keeps `task-06f-ingestion-validation` as `PENDING`.

## Routed package

- **Implementation level:** Level 2 (PC), with required SURGICAL review before closure.
- **Assigned role:** PC (review handoff posture).
- **Task ID:** `task-06f-ingestion-validation` (work package context: `BE-06F-A` in `.opencode/task-plan.hierarchy.json`).
- **Dependencies:** `task-06e-child-process:ACCEPTED` already satisfied.
- **allowed_paths:** `src/test/resources/application.yml`, `.opencode/current/PC/**` (only if REVISE arrives).
- **Exact gate:** `./scripts/task-gate.sh task-06f-ingestion-validation`.
- **Required SURGICAL review:** **Yes (mandatory)**. Closure requires Codex `ACCEPT`.

## One-pass next action

1. Hold backend editing and submit/retain the current green evidence packet for SURGICAL Codex review.
2. If and only if Codex returns `REVISE`, run one bounded correction pass under `BE-06F-A` scope and rerun the exact gate.

## Acceptance conditions

- Codex decision for this task becomes `ACCEPT`.
- Exact gate remains green on the accepted state.
- No out-of-scope backend changes are introduced while waiting for review.

## Avoid repeating

- Do **not** rerun expensive backend gates or open new code edits while this same checkpoint is still awaiting Codex decision.
