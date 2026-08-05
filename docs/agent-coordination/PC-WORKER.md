# PC code review (backend)

## Evidence reviewed

- `pc-runtime/progress.json`: active task is `task-06e-child-process`, status still `PENDING`, last gate-green metadata recorded.
- `pc-runtime/gate_summary.md`: deterministic gate classification `green`, exit `0`.
- `pc-runtime/codex-qwen3-extra-instructions.md`: latest Codex decision context is `REVISE` with mandatory bounded packet for test-only correction scope.
- `pc-runtime/manifest.json`: `codex_review`, `codex_plan`, `local_understanding`, and `checkpoint` are `null` for the latest run snapshot.
- `pc-git-status.txt` / `pc-git-diff-stat.txt`: no current dirty diff captured in this snapshot.

## First current defect

The first backend defect is **workflow closure evidence missing**: gate is green, but there is no Codex decision artifact proving `ACCEPT`, and the task remains pending.

## Why this matters

Per queue rules, gate-green alone does not complete a task. Without Codex `ACCEPT`, task-06e cannot be closed and downstream tasks (06f/07/08/09) should not advance.

## Bounded next action for one worker pass

1. Stay on `task-06e-child-process`.
2. Perform a focused review against Codex packet requirements already listed in `pc-runtime/codex-qwen3-extra-instructions.md`.
3. Submit the current gate-green snapshot for Codex decision.
4. Edit only if the review finds a concrete mismatch; keep edits bounded to the allowed test files/resources.

## Acceptance conditions

- `./scripts/task-gate.sh task-06e-child-process` returns exit `0`.
- Codex decision is explicitly `ACCEPT` for `task-06e-child-process`.
- No scope expansion into production code/scripts.

## Avoid repeating

- Do not run another unchanged no-product-diff pass that still yields no Codex decision artifact.
