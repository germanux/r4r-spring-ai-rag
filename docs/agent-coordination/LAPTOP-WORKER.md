# LP Code Review (Frontend)

## Evidence reviewed (current RUN_DIR)

- `runtime/ring-agent/ring/20260807T014529Z/lp-runtime/progress.json`
- `runtime/ring-agent/ring/20260807T014529Z/lp-runtime/memory.md`
- `runtime/ring-agent/ring/20260807T014529Z/lp-runtime/gate_summary.md`
- `runtime/ring-agent/ring/20260807T014529Z/lp-runtime/codex_plan.json`
- `runtime/ring-agent/ring/20260807T014529Z/lp-runtime/codex-qwen3-extra-instructions.md`
- `runtime/ring-agent/ring/20260807T014529Z/lp-git-status.txt`

## Current diagnosis

`task-fe-03d-dom-state-tests` remains pending with the latest deterministic gate failure (`exit code: 2`). The active Codex packet is explicit: this is a one-file local spec correction with known anti-patterns to remove and three prescribed DOM tests to implement safely. No newer LP execution evidence exists in this RUN_DIR to show completion.

## Directed next package

- **Implementation level:** Level 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** `task-fe-03c-citations:ACCEPTED` (already satisfied per progress evidence)
- **allowed_paths (canonical):** `frontend/**`, `docs/frontend/**` (effective narrow focus path in packet: `frontend/src/app/features/rag/rag-page.component.spec.ts`)
- **Next action (single worker pass):** complete exactly one bounded spec-file correction per Codex plan/extra instructions, then run deterministic gates once.

## Exact gate and acceptance conditions

1. `git diff --check`
2. `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
3. Closure policy: exact-gate-green + scope-clean + controller-commit
4. Patch quality constraints from packet:
   - preserve valid existing suite coverage,
   - remove malformed structure and prohibited patterns,
   - include only the three required DOM tests,
   - keep diagnostics/understanding consistent with final patch and executed gate.

## Avoid repeating

Do **not** reintroduce malformed braces/suite structure, trailing whitespace, internal-state mutation, `innerHTML` mutation, guessed selectors, or other explicitly rejected patterns.
