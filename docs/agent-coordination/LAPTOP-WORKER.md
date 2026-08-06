# LP code review — run 20260806T195634Z

## Evidence reviewed

- `runtime/ring-agent/ring/20260806T195634Z/lp-runtime/progress.json`
- `runtime/ring-agent/ring/20260806T195634Z/lp-runtime/gate_summary.md`
- `runtime/ring-agent/ring/20260806T195634Z/lp-runtime/codex-qwen3-extra-instructions.md`
- `runtime/ring-agent/ring/20260806T195634Z/lp-runtime/memory.md`
- `runtime/ring-agent/ring/20260806T195634Z/lp-git-status.txt`

## First current defect (LP)

The deterministic frontend task gate is currently failing for the active task:

- Task: `task-fe-03d-dom-state-tests`
- Gate classification: `gate-failure`
- Exit code: `2`
- Codex decision: `REVISE` with explicit bounded corrections in `rag-page.component.spec.ts`.

## Bounded next action package

- **Implementation level:** 1 (LP)
- **Assigned role:** LP (`laptop-qwen3-worker`)
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:**
  - `task-fe-03c-citations:ACCEPTED` (already true in progress evidence)
  - Active Codex REVISE packet (current correction authority)
- **allowed_paths:**
  - `frontend/src/app/features/rag/rag-page.component.spec.ts` (single-file bounded correction for this pass)
- **Exact gate:**
  - `git diff --check`
  - `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
- **Required SURGICAL review:** mandatory post-gate Codex `ACCEPT` before task closure.

## Required implementation content for this pass

Apply only the active Codex packet:

1. Remove defective speculative additions from attempt-06.
2. Implement one controlled-pending loading test.
3. Implement one independent success-reset test (fixture-rendered DOM assertions).
4. Implement one independent transport-error reset test (fresh Subject).
5. Preserve existing valid coverage and keep diagnostics internally consistent.

## Acceptance conditions for this pass

1. Non-empty, single-file scoped patch under the allowed path.
2. `git diff --check` passes.
3. `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests` exits `0`.
4. Codex review returns `ACCEPT` and hierarchy closure conditions are satisfied.

## Avoid repeating

Do **not** reintroduce synthetic tests, manual loading flag mutation, direct `innerHTML` mutation, invalid response shapes, or selector guesses already rejected by Codex.
