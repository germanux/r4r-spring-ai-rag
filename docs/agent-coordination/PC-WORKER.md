# PC code review (Ring)

## Current queue status
- **Implementation level:** Level 2 (PC)
- **Assigned role:** PC
- **Active task:** `task-07-populate-production-rag`
- **Related work package:** `BE-07-B` (blocked by `BE-07-A`)
- **Decision this cycle:** **HOLD**

## First current defect
PC activity is currently out of sequence with hierarchy dependencies: `BE-07-B` cannot proceed until `BE-07-A:ACCEPTED`, but current PC evidence already contains backend product edits and a red gate summary in task-07 context.

## Evidence reviewed
- `runtime/ring-agent/ring/20260806T155109Z/pc-runtime/progress.json`
- `runtime/ring-agent/ring/20260806T155109Z/pc-git-status.txt`
- `runtime/ring-agent/ring/20260806T155109Z/pc-runtime/gate_summary.md`
- `runtime/ring-agent/ring/20260806T155109Z/pc-runtime/previous-ring-qwen3-directive.json`
- `.opencode/task-plan.hierarchy.json`

## Bounded next action package
- **Task ID:** `task-07-populate-production-rag`
- **Owner:** PC (Level 2)
- **Dependencies:** `BE-07-A:ACCEPTED` required before `BE-07-B`
- **allowed_paths (canonical):** for `BE-07-B`: `src/**`, `docs/backend/**`
- **Exact gate:** `./scripts/task-gate.sh all` (as declared for `BE-07-B` in hierarchy plan)
- **Required SURGICAL review:** Mandatory before closure per review policy

### One-pass action
Keep PC idle for one pass. Do not rerun backend gates and do not add new backend edits until newer evidence proves `BE-07-A` acceptance.

## Acceptance conditions
1. New run evidence explicitly shows `BE-07-A` accepted/unblocked.
2. PC resumes exactly one first-failure correction pass within `BE-07-B` scope.
3. Exact gate evidence is captured.
4. SURGICAL Codex returns `ACCEPT` before closure.

## Avoid repeating
- Re-running task-07/all backend gates while dependency remains blocked.
- Producing additional backend diffs without prerequisite acceptance evidence.
