# PC code review — run 20260806T150915Z

## Current evidence verdict
- **First current defect:** PC is positioned on `task-07-populate-production-rag` while the hierarchy prerequisite for its executable package is still unsatisfied (`BE-07-A` not accepted).
- **Observed state in this run snapshot:**
  - Active backend task remains `task-07-populate-production-rag` (`pc-runtime/progress.json`).
  - Current PC gate evidence is red (`test-failure`) and points to backend tests (`pc-runtime/gate_summary.md`).
  - No task-owned product diff exists; only memory file changed (`pc-git-status.txt`, `pc-git-diff-stat.txt`).

## Bounded next action package
- **Implementation level:** Level 2 (PC)
- **Assigned role:** PC (backend)
- **Task ID:** `task-07-populate-production-rag`
- **Dependencies:** `BE-07-A:ACCEPTED` before BE-07-B execution
- **allowed_paths:** `src/**`, `docs/backend/**` (for BE-07-B from hierarchy)
- **Exact gate:** `./scripts/task-gate.sh all` (then task-07 command sequence per `.opencode/task-plan.backend.json`)
- **Required SURGICAL review:** Mandatory before closure (`ACCEPT` required)

### This pass instruction
**HOLD the PC queue for one pass.** Do not rerun task-07 or full backend gates until acceptance evidence for `BE-07-A` is present. After dependency unlock, execute exactly one first-failure correction cycle.

## Acceptance conditions (for release from HOLD)
1. Evidence shows `BE-07-A` accepted.
2. PC produces a task-owned bounded diff within allowed paths.
3. Exact gate evidence is fresh for the current attempt.
4. SURGICAL Codex returns `ACCEPT` before closure.

## Avoid repeating
- Re-running expensive backend gates with unchanged dependency state.
- Producing memory-only updates without task-owned product evidence.
