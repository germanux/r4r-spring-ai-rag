# LP code review (frontend)

## Evidence reviewed (current RUN_DIR)

- `runtime/ring-agent/ring/20260807T030125Z/lp-runtime/progress.json`
- `runtime/ring-agent/ring/20260807T030125Z/lp-runtime/controller_state.json`
- `runtime/ring-agent/ring/20260807T030125Z/lp-runtime/codex-qwen3-extra-instructions.md`
- `runtime/ring-agent/ring/20260807T030125Z/lp-git-status.txt`
- `runtime/ring-agent/ring/20260807T030125Z/lp-git-diff-stat.txt`
- `runtime/ring-agent/ring/20260807T030125Z/lp-runtime/previous-ring-qwen3-directive.json`

## First current defect

The first defect is **controller guardrail stop**, not missing correction guidance.

- `lp-runtime/controller_state.json` reports `GLOBAL_ATTEMPT_LIMIT_REACHED` with `attempts: 17`, `limit: 6`.
- `lp-runtime/progress.json` keeps `task-fe-03d-dom-state-tests` as `BLOCKED`.
- A specific Codex REVISE packet already exists (`lp-runtime/codex-qwen3-extra-instructions.md`), but cannot be executed until attempt budget is rearmed.

## Directed bounded package (post-rearm)

- **Implementation level:** Level 1 (LP)
- **Assigned role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:**
  - Attempt-budget reset/rearm by operator/controller
  - `task-fe-03c-citations:ACCEPTED` (already satisfied)
- **allowed_paths (narrowed):**
  - `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate(s):**
  1. `git diff --check`
  2. `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`

## One-pass next action after reset

Use only the existing Codex REVISE correction packet to perform one bounded edit pass in `rag-page.component.spec.ts`:

1. restore valid spec structure,
2. add one controlled-pending loading/duplicate-submit DOM test,
3. add one success-reset DOM test,
4. add one transport-error-reset DOM test,
5. preserve existing valid answer/abstention/citation/escaping coverage.

## Acceptance conditions

Accept only with:

1. exact gate green,
2. scope-clean,
3. controller commit evidence for task closure.

## Avoid repeating

Do **not** run another broad malformed spec rewrite, and do **not** attempt FE-03D execution before attempt-budget rearm.
