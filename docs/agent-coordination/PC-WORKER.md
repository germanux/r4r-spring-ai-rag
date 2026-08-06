# PC code review (RUN 20260806T143139Z)

## Current evidence

- Active backend task in progress file: `task-07-populate-production-rag` (`pc-runtime/progress.json`).
- No task-07 gate execution evidence in this snapshot; memory still says "latest exact gate: not run" (`pc-runtime/memory.md`).
- PC worktree snapshot is only memory-file dirty, with no product-path implementation diff shown (`pc-git-status.txt`, `pc-git-diff-stat.txt`).
- Work package dependency chain in hierarchy requires **BE-07-A accepted before BE-07-B** (`.opencode/task-plan.hierarchy.json`).

## First current defect

Task sequencing is not ready for implementation pass: PC is positioned on task-07, but the dependency gate for the executable package (`BE-07-B`) is not evidenced as satisfied in this run.

## Directed next action package

- **Implementation level:** 2 (PC queue held pending dependency)
- **Assigned role:** PC
- **Task ID:** `task-07-populate-production-rag` (target package `BE-07-B`)
- **Dependencies:** `BE-07-A:ACCEPTED` (required), backend-phase active
- **allowed_paths:** from parent task plan for task-07 (`pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`); do not write while held
- **Exact gate:** `task-07` backend gate command from `.opencode/task-plan.backend.json`
- **Required SURGICAL review:** mandatory before closure per `.opencode/task-plan.hierarchy.json` review policy

### One-pass instruction

Hold PC queue and perform **no task-07 edits** until dependency acceptance is explicit in authoritative progress evidence.

## Acceptance conditions for releasing HOLD

1. Evidence shows `BE-07-A` accepted (or equivalent accepted prerequisite under controller authority).
2. Backend phase ownership for this pass is explicit and non-overlapping.
3. PC then executes one bounded task-07 pass and stops again for SURGICAL review.

## Avoid repeating

Do not run expensive task-07 backend gates before dependency acceptance is proven.
