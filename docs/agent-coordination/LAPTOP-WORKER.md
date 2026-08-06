# LP code review (Ring)

## Current evidence-based status

- Active task: `task-fe-03d-dom-state-tests` (`lp-runtime/progress.json`).
- Latest gate is red: exit `2` (`lp-runtime/memory.md`, `lp-runtime/gate_summary.md`).
- Codex disposition is `REVISE` with explicit corrections targeting invalid synthetic tests and inconsistent evidence packaging (`lp-runtime/codex-qwen3-extra-instructions.md`).
- Current LP product diff exists in `frontend/src/app/features/rag/rag-page.component.spec.ts` (`lp-git-status.txt`, `lp-git-diff-stat.txt`).

## First current defect (LP)

The first defect is **incorrect FE-03D test implementation** in `rag-page.component.spec.ts`: added synthetic patterns and invalid constructs do not prove the required DOM behavior and are called out directly by Codex.

## Bounded next action package

- **Implementation level:** Level 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:**
  - Follow the active Codex `REVISE` packet exactly.
  - Keep frontend queue isolated from backend work.
- **allowed_paths:**
  - Canonical task scope: `frontend/**`, `docs/frontend/**` (`.opencode/task-plan.frontend.json`)
  - Active correction constraint (stricter): `frontend/src/app/features/rag/rag-page.component.spec.ts` only (`codex-qwen3-extra-instructions.md`)
- **Exact gate:** `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests` after `git diff --check`.
- **Required SURGICAL review:** Yes (mandatory before closure under hierarchy policy).

## Acceptance conditions

1. Replace defective added tests with the prescribed:
   - one controlled-pending loading/disabled DOM test,
   - one success-reset DOM test,
   - one transport-error-reset DOM test.
2. Preserve existing valid answer/abstention/citation/escaping/service-isolation coverage.
3. Hygiene passes: `git diff --check` clean.
4. Exact FE-03D gate exits `0`.
5. Evidence consistency: changed paths, `task-gate.json`, and full gate log must describe the same final run.
6. SURGICAL Codex returns `ACCEPT` for closure.

## Avoid repeating

- Do not reintroduce synthetic tests, fake state fields, direct `innerHTML` mutation, or stale/mismatched diagnostic bundles.

## Evidence paths

- `runtime/ring-agent/ring/20260806T190129Z/lp-runtime/memory.md`
- `runtime/ring-agent/ring/20260806T190129Z/lp-runtime/gate_summary.md`
- `runtime/ring-agent/ring/20260806T190129Z/lp-runtime/codex-qwen3-extra-instructions.md`
- `runtime/ring-agent/ring/20260806T190129Z/lp-git-status.txt`
