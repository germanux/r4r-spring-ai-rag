# LP code review (evidence cycle 20260807T012527Z)

## Current evidence

- Active LP task is `task-fe-03d-dom-state-tests` (`lp-runtime/progress.json`).
- Gate summary is failing with exit code `2` (`lp-runtime/gate_summary.md`).
- Codex plan marks decision `READY` with bounded corrections in one file (`lp-runtime/codex_plan.json`).
- Extra instructions require a strict single-file revision and exact re-gate (`lp-runtime/codex-qwen3-extra-instructions.md`).
- LP working diff is limited to one spec file (`lp-git-status.txt`).

## First current defect (LP)

The current defect remains a **local spec-file correctness issue** in `frontend/src/app/features/rag/rag-page.component.spec.ts`: invalid structure/format and rejected testing patterns must be removed, and three prescribed DOM tests must be restored/added exactly.

## Bounded next action package

- **Implementation level:** Level 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** `task-fe-03c-citations` accepted (already satisfied)
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate:**
  1. `git diff --check`
  2. `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`

## Required content for this pass

1. Restore valid suite structure and balanced braces.
2. Keep existing valid answer/abstention/citation/error/escaping coverage.
3. Add only the prescribed tests:
   - controlled-pending loading + duplicate submit suppression
   - independent success-reset DOM cleanup
   - independent transport-error-reset DOM cleanup
4. No forbidden patterns from Codex packet (synthetic fields, internal-state mutation, guessed selectors, `of`/`tick` misuse, trailing whitespace).

## Avoid repeating

Do not repeat malformed spec structure or previously rejected shortcuts; follow the Codex packet literally and keep the edit bounded to one observable behavior in one file.
