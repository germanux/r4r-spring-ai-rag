# LP code review (frontend)

## Evidence reviewed (RUN_DIR authoritative)

- `runtime/ring-agent/ring/20260807T014029Z/lp-runtime/gate_summary.md`
- `runtime/ring-agent/ring/20260807T014029Z/lp-runtime/codex_plan.json`
- `runtime/ring-agent/ring/20260807T014029Z/lp-runtime/codex-qwen3-extra-instructions.md`
- `runtime/ring-agent/ring/20260807T014029Z/lp-runtime/progress.json`
- `runtime/ring-agent/ring/20260807T014029Z/lp-git-status.txt`
- `runtime/ring-agent/ring/20260807T014029Z/lp-git-diff-stat.txt`

## First current defect

The first current frontend defect is an **unfinished FE-03D one-file spec correction**:

- Active task is `task-fe-03d-dom-state-tests`.
- Gate summary is failing (`exit 2`).
- Codex plan classifies the defect as local test-file structure/content issues and prescribes a bounded fix.
- Current LP diff remains inside `frontend/src/app/features/rag/rag-page.component.spec.ts`, which is appropriate for LP level-1 scope.

## Directed work package

- **Implementation level:** Level 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** `task-fe-03c-citations: ACCEPTED` (satisfied)
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts`

### One focused next action (single worker pass)

Apply exactly the active Codex correction packet in one file:

1. Restore valid suite structure and remove prohibited attempt patterns.
2. Keep/add only the prescribed DOM tests (controlled pending loading, success-reset, transport-error-reset).
3. Run `git diff --check`.
4. Run `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests` once.

### Exact acceptance gates

- `git diff --check`
- `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
- Closure policy: `exact-gate-green + scope-clean + controller-commit`

## Avoid repeating

Do not reintroduce malformed braces, trailing whitespace, internal-state mutation (`component.isLoading` / synthetic `currentState`), `innerHTML` mutation, guessed selectors, or unnecessary `of`/`tick` usage rejected by the Codex packet.
