# LP code review — run 20260807T005023Z

## Evidence reviewed

- `runtime/ring-agent/ring/20260807T005023Z/lp-runtime/codex-qwen3-extra-instructions.md`
- `runtime/ring-agent/ring/20260807T005023Z/lp-runtime/progress.json`
- `runtime/ring-agent/ring/20260807T005023Z/lp-runtime/memory.md`
- `runtime/ring-agent/ring/20260807T005023Z/lp-runtime/previous-ring-qwen3-directive.json`
- `runtime/ring-agent/ring/20260807T005023Z/lp-git-status.txt`
- `runtime/ring-agent/ring/20260807T005023Z/lp-git-diff-stat.txt`

## Current diagnosis (first defect)

Active frontend task is `task-fe-03d-dom-state-tests` with one modified file and an explicit Codex `REVISE` packet. The current snapshot shows no new gate execution proof for this run, so the first defect remains **spec correction quality in `rag-page.component.spec.ts`**.

## Bounded next package

- **Implementation level:** Level 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** `task-fe-03c-citations` accepted (already true per progress)
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate:**
  1. `git diff --check`
  2. `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
  3. Closure policy: exact-gate-green + scope-clean + controller commit

## Mandatory correction content from evidence packet

1. Remove rejected synthetic/manual patterns and structural damage.
2. Add one controlled-pending loading/duplicate-submission test.
3. Add one independent success-reset test with citations.
4. Add one independent transport-error-reset test with a fresh Subject.
5. Preserve existing valid answer/abstention/citation/error/escaping coverage.

## Acceptance evidence required

1. Non-empty scoped patch limited to the single spec file.
2. `git diff --check` clean.
3. FE-03D gate exits 0.
4. Local understanding maps selectors/assertions to all required DOM behaviors.

## Avoid repeating

Do not reintroduce previously rejected patterns: `innerHTML` mutation, manual loading-flag mutation, guessed selectors, invalid response shapes, unnecessary `of/tick`, or brace/indentation regressions.
