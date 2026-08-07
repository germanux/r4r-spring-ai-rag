# LP code review (frontend)

## Evidence reviewed

- `runtime/ring-agent/ring/20260807T013528Z/lp-runtime/gate_summary.md`
- `runtime/ring-agent/ring/20260807T013528Z/lp-runtime/codex_plan.json`
- `runtime/ring-agent/ring/20260807T013528Z/lp-runtime/codex-qwen3-extra-instructions.md`
- `runtime/ring-agent/ring/20260807T013528Z/lp-runtime/progress.json`
- `runtime/ring-agent/ring/20260807T013528Z/lp-git-status.txt`
- `runtime/ring-agent/ring/20260807T013528Z/lp-git-diff-stat.txt`

## Current diagnosis

The first current defect is a **single-file FE-03D spec correction still incomplete**:

- Active task is `task-fe-03d-dom-state-tests`.
- Latest LP gate summary is `gate-failure` with `exit 2`.
- Codex packet explicitly classifies this as local test-file defects (format/suite structure/forbidden patterns), with a bounded repair plan in one file.
- Current LP diff remains limited to `frontend/src/app/features/rag/rag-page.component.spec.ts`, which is scope-correct for a level-1 pass.

## Directed next package

- **Implementation level:** Level 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** `task-fe-03c-citations: ACCEPTED` (already satisfied)
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts` (narrower than task-level `frontend/**` and preferred for this correction)

### One-pass action

Apply the active Codex corrections exactly:

1. Restore valid suite structure and remove prohibited attempt patterns.
2. Keep/add only the prescribed DOM tests (controlled pending loading + success-reset + transport-error-reset).
3. Run `git diff --check` first.
4. Run `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests` once.

### Exact acceptance gate

- `git diff --check`
- `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
- Closure policy: exact-gate-green + scope-clean + controller-commit

## Avoid repeating

Do not reintroduce malformed brace structure, trailing whitespace, internal state mutation (`component.isLoading`/synthetic state), `innerHTML` mutation, guessed `data-testid` selectors, or unnecessary `of`/`tick` usage already rejected by Codex.
