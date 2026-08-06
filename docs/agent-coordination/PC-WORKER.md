# PC code review (Ring)

## Current evidence snapshot
- Active task: `task-06f-ingestion-validation` (`pc-runtime/progress.json`)
- Gate state: **green**, exit `0` (`pc-runtime/gate_summary.md`)
- Checkpoint: `no-product-diff`, `head_after: null` (`pc-runtime/checkpoint.json`)
- Worker request reason: `gate-green-no-checkpoint` with `codex_decision: null` (`worker-requests/PC.json`)
- PC worktree diff: only `.opencode/memory.backend.md` dirty (`pc-git-status.txt`)

## First current defect
The first blocker is **missing SURGICAL closure decision**, not backend implementation failure. The task is still `PENDING` despite a green exact gate because Codex/SURGICAL acceptance is not recorded.

## Bounded next action package
- **Work package:** `BE-06F-A`
- **Implementation level:** **Level 2**
- **Assigned role:** **PC** (with mandatory **SURGICAL** review before closure)
- **Task ID:** `task-06f-ingestion-validation`
- **Dependencies:** `task-06e-child-process:ACCEPTED`
- **allowed_paths:** `src/test/resources/application.yml`, `.opencode/current/PC/**`
- **Exact gate:** `./scripts/task-gate.sh task-06f-ingestion-validation`
- **Required SURGICAL review:** `ACCEPT` required by `.opencode/task-plan.hierarchy.json` review policy

### One-pass instruction
Do **not** start new backend edits first. Route the current gate-green package for one SURGICAL decision pass (`ACCEPT` or `REVISE`). If `REVISE` is returned, execute only the first concrete correction inside `BE-06F-A` scope, then re-run the exact gate.

## Acceptance conditions
1. SURGICAL returns explicit `ACCEPT` for the current package, or explicit `REVISE` with bounded correction instructions.
2. Exact gate remains green (`./scripts/task-gate.sh task-06f-ingestion-validation`).
3. No scope expansion beyond `BE-06F-A` unless a new first failure requires formal reclassification.

## Avoid repeating
- Re-running unchanged backend gates without a new failure signal.
- Advancing to `task-07` before `task-06f` has SURGICAL acceptance evidence.
