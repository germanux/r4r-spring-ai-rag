# LP code review (frontend)

## Evidence reviewed

- `runtime/ring-agent/ring/20260807T024439Z/lp-runtime/controller_state.json`
- `runtime/ring-agent/ring/20260807T024439Z/lp-runtime/progress.json`
- `runtime/ring-agent/ring/20260807T024439Z/lp-runtime/codex-qwen3-extra-instructions.md`
- `runtime/ring-agent/ring/20260807T024439Z/lp-git-status.txt`
- `runtime/ring-agent/ring/20260807T024439Z/lp-git-diff-stat.txt`

## First current defect

The first active defect is **execution blocked by controller guardrail**:

- `controller_state.json` reports `GLOBAL_ATTEMPT_LIMIT_REACHED` (exit 70, attempts 17, limit 6).
- Active task remains `task-fe-03d-dom-state-tests` with status `BLOCKED` in `progress.json`.

So LP cannot produce fresh gate evidence until attempt budget is reset/rearmed.

## Bounded next package (post-reset)

- **Implementation level:** Level 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:**
  - attempt-budget reset/rearm by controller/supervisor
  - `task-fe-03c-citations:ACCEPTED` (already satisfied)
- **allowed_paths (narrowed):**
  - `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate:**
  1. `git diff --check`
  2. `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`

## Prescribed one-pass correction

Use the existing Codex REVISE packet only:

1. Revert defective additions and restore valid spec structure.
2. Add exactly one controlled-pending loading/duplicate-submit DOM test.
3. Add one success-reset DOM test.
4. Add one transport-error-reset DOM test.
5. Preserve existing valid answer/abstention/citation/escaping coverage.

## Acceptance evidence required

Accept only with exact-gate-green + scope-clean + controller commit evidence.

## Avoid repeating

Do **not** perform another broad or malformed spec rewrite before applying the one-file correction packet.
