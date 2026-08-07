# LP code review (run 20260807T021032Z)

## Evidence reviewed

- `runtime/ring-agent/ring/20260807T021032Z/lp-runtime/gate_summary.md`
- `runtime/ring-agent/ring/20260807T021032Z/lp-runtime/codex-qwen3-extra-instructions.md`
- `runtime/ring-agent/ring/20260807T021032Z/lp-runtime/memory.md`
- `runtime/ring-agent/ring/20260807T021032Z/lp-runtime/progress.json`
- `runtime/ring-agent/ring/20260807T021032Z/lp-git-status.txt`
- `runtime/ring-agent/ring/20260807T021032Z/lp-git-diff-stat.txt`

## First current defect

The active FE-03D task remains red and unresolved in one file:

- Deterministic gate summary reports `exit code: 2`.
- Codex packet is `REVISE` with explicit one-file repair instructions.
- Previous local pass timed out (`session-timeout`) and did not produce acceptance evidence.
- Dirty state persists at `frontend/src/app/features/rag/rag-page.component.spec.ts`.

## Bounded next package

- **Implementation level:** Level 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** `task-fe-03c-citations: ACCEPTED`
- **allowed_paths:**
  - `frontend/src/app/features/rag/rag-page.component.spec.ts`

### One focused next action (single pass)

Apply exactly the existing FE-03D correction packet in `rag-page.component.spec.ts`:

1. Restore valid suite structure and remove prohibited additions (synthetic fields/selectors, internal-state mutations, `innerHTML` mutation, `of`/`tick` misuse).
2. Keep the three prescribed tests only: controlled-pending loading/duplicate-submit, success-reset with citations, transport-error-reset.
3. Preserve existing valid answer/abstention/citation/escaping/service-isolation coverage.

### Exact deterministic gate

1. `git diff --check`
2. `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
3. Closure policy: `exact-gate-green + scope-clean + controller-commit`

## Acceptance evidence required

- Gate exit `0` for FE-03D exact command.
- Scoped diff only in `frontend/src/app/features/rag/rag-page.component.spec.ts`.
- Requirement-to-selector assertion map present in local understanding, including:
  - loading status: `.loading-state[role="status"]`
  - disabled controls: `textarea`, `.submit-button`
  - error alert: `.error-state[role="alert"]`
  - answer visibility: `.answer-content`
  - reset cleanup: absence of answer/citation/error + presence of `.idle-state`

## Avoid repeating

Do not rerun with malformed test structure, guessed selectors, or timeout-prone no-plan retries.
