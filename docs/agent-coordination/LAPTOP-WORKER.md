# LP code review (current cycle)

## Evidence reviewed

- `runtime/ring-agent/ring/20260807T020532Z/lp-runtime/gate_summary.md`
- `runtime/ring-agent/ring/20260807T020532Z/lp-runtime/codex-qwen3-extra-instructions.md`
- `runtime/ring-agent/ring/20260807T020532Z/lp-runtime/memory.md`
- `runtime/ring-agent/ring/20260807T020532Z/lp-runtime/progress.json`
- `runtime/ring-agent/ring/20260807T020532Z/lp-git-status.txt`
- `runtime/ring-agent/ring/20260807T020532Z/lp-git-diff-stat.txt`

## Current diagnosis

The first current LP defect remains the FE-03D one-file spec regression:

- Deterministic gate summary is red (`exit code: 2`).
- Codex correction packet is `REVISE` with explicit constraints for the same file.
- Prior local pass timed out (`session-timeout`) and did not produce acceptance evidence.
- Dirty state persists in one file: `frontend/src/app/features/rag/rag-page.component.spec.ts`.

## Bounded next package

- **Implementation level:** Level 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** `task-fe-03c-citations: ACCEPTED` (already evidenced)
- **allowed_paths:**
  - `frontend/src/app/features/rag/rag-page.component.spec.ts`

### Exact next action for one pass

Apply exactly the existing FE-03D correction packet in `rag-page.component.spec.ts`:

1. Restore valid suite structure and remove defective attempt additions called out in the packet.
2. Keep only the three prescribed tests (controlled-pending loading/duplicate-submission, success-reset, transport-error-reset) using fixture-rendered DOM assertions.
3. Preserve existing valid answer/abstention/citation/escaping/service-isolation coverage.

### Exact gate

1. `git diff --check`
2. `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
3. Closure policy: `exact-gate-green + scope-clean + controller-commit`

## Acceptance evidence required

- Gate exit `0` for FE-03D.
- Scoped diff only in `frontend/src/app/features/rag/rag-page.component.spec.ts`.
- Local understanding/report maps requirements to concrete selectors asserted in DOM:
  - loading status: `.loading-state[role="status"]`
  - disabled controls: `textarea`, `.submit-button`
  - error alert: `.error-state[role="alert"]`
  - answer visibility: `.answer-content`
  - reset cleanup: absence of answer/citation/error + presence of `.idle-state`

## Avoid repeating

Do not reintroduce malformed test structure, internal state mutations, guessed selectors, `innerHTML` mutation, or timeout-prone reruns without plan changes.
