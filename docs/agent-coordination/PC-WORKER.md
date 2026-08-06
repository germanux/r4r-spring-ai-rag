# PC code review (Ring)

## Evidence read first (RUN_DIR)
- `pc-runtime/progress.json`
- `pc-runtime/gate_summary.md`
- `pc-runtime/previous-ring-qwen3-directive.json`
- `pc-git-status.txt`
- `pc-git-diff-stat.txt`
- `.opencode/task-plan.hierarchy.json`

## First current defect
PC is attempting/positioned for `task-07-populate-production-rag` while hierarchy prerequisites remain unmet. In the canonical hierarchy, `BE-07-B` (PC) depends on `BE-07-A:ACCEPTED`, and `BE-07-A` is still `PENDING`. The latest PC evidence also shows a failing gate summary and no task-scoped product diff in this snapshot.

## Decision
- **Implementation level:** Level 2 (PC), but **held by dependency**
- **Assigned role:** PC
- **Task ID:** `task-07-populate-production-rag` (work package dependency: `BE-07-A -> BE-07-B`)
- **Dependencies:** `BE-07-A:ACCEPTED` and backend-phase activation evidence
- **allowed_paths (when unblocked):** per directive and backend plan (`pom.xml`, `src/main/**`, `src/test/**`, `docs/backend/**`)
- **Exact gate (when unblocked):** task-07 gate command in `.opencode/task-plan.backend.json`
- **Required SURGICAL review:** Mandatory before closure per hierarchy `review_policy`

## Bounded next action for one worker pass
**Do not run PC backend gates now.** Hold the queue until BE-07-A is accepted; then resume with one bounded first-failure pass only.

## Acceptance conditions
1. BE-07-A accepted with direct evidence.
2. PC resumes within declared `allowed_paths` only.
3. Exact task-07 gate result is captured.
4. SURGICAL Codex returns `ACCEPT` before closure.

## Avoid repeating
Do not repeat backend gate cycles without changed dependency state or changed code evidence.
