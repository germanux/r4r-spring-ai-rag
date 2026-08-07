# LP code review (evidence-grounded)

## Current diagnosis

- Active frontend task is `task-fe-03d-dom-state-tests`.
- `lp-runtime/controller_state.json` is `GLOBAL_ATTEMPT_LIMIT_REACHED` (`attempts: 17`, `limit: 6`, exit `70`).
- `lp-runtime/progress.json` still marks task `BLOCKED`.
- A large uncommitted diff remains in one file (`frontend/src/app/features/rag/rag-page.component.spec.ts`; 109 insertions/20 deletions in this snapshot).
- `lp-runtime/codex-qwen3-extra-instructions.md` contains a precise one-file `REVISE` correction packet that has not yet been completed with new green gate evidence.

Primary defect is **execution control blockage** (attempt-limit stop), with unresolved one-file correction work still pending.

## Directed next package

- **Implementation level:** Level 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** `task-fe-03c-citations` accepted (already satisfied)
- **allowed_paths (canonical):** `frontend/**`, `docs/frontend/**`
- **Narrowed write scope for this pass:** `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Action state now:** HOLD until attempt budget is reset/rearmed by controller policy; then execute exactly one bounded repair pass.

## Exact gate and acceptance conditions (when unblocked)

1. Apply only the existing Codex FE-03D correction packet in the spec file.
2. `git diff --check`
3. `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
4. Closure policy evidence present: `exact-gate-green + scope-clean + controller-commit`

### Must be explicitly proven in that pass

- Controlled pending submission shows loading status and disabled controls.
- Success path plus reset clears answer/citations/error and restores idle state.
- Transport-error path plus reset clears alert and restores idle state.
- Existing valid answer/abstention/citation/escaping coverage remains intact.

## Avoid repeating

- Do **not** run broad retries or restructure tests outside the correction packet.
- Do **not** rerun the gate before the one-file repair is applied.

## Evidence consulted

- `runtime/ring-agent/ring/20260807T023359Z/lp-runtime/controller_state.json`
- `runtime/ring-agent/ring/20260807T023359Z/lp-runtime/progress.json`
- `runtime/ring-agent/ring/20260807T023359Z/lp-runtime/codex-qwen3-extra-instructions.md`
- `runtime/ring-agent/ring/20260807T023359Z/lp-git-status.txt`
- `runtime/ring-agent/ring/20260807T023359Z/lp-git-diff-stat.txt`
- `runtime/ring-agent/ring/20260807T023359Z/worker-requests/LP.json`
