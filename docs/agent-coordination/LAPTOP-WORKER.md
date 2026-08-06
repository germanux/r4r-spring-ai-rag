# LP code review (Ring cycle 20260806T191130Z)

## Evidence reviewed

- `runtime/ring-agent/ring/20260806T191130Z/lp-runtime/progress.json`
- `runtime/ring-agent/ring/20260806T191130Z/lp-runtime/memory.md`
- `runtime/ring-agent/ring/20260806T191130Z/lp-runtime/gate_summary.md`
- `runtime/ring-agent/ring/20260806T191130Z/lp-runtime/codex-qwen3-extra-instructions.md`
- `runtime/ring-agent/ring/20260806T191130Z/lp-git-diff-stat.txt`

## First current defect

`task-fe-03d-dom-state-tests` remains red (`exit 2`). The active Codex REVISE packet reports that recently added tests are synthetic/invalid and do not prove required DOM behavior, specifically around loading and reset semantics.

## Bounded correction package

- **Implementation level:** 1 (LP)
- **Assigned role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:**
  - Use current Codex correction packet exactly.
  - Keep existing accepted FE-03C coverage intact.
- **allowed_paths:**
  - Canonical task scope: `frontend/**`, `docs/frontend/**` (from `.opencode/task-plan.frontend.json`)
  - Codex-constrained correction scope for this pass: `frontend/src/app/features/rag/rag-page.component.spec.ts` only
- **Exact gate:** `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
- **Required SURGICAL review:** mandatory after gate result before closure.

## Required implementation content for this single pass

1. Remove defective synthetic tests introduced in the latest failed attempt.
2. Restore one controlled pending-observable loading test with DOM assertions on:
   - `.loading-state[role="status"]`
   - `textarea`
   - `.submit-button`
   - plus one-call-only behavior while still pending.
3. Split reset coverage into two independent tests:
   - success-reset path (answer/citations shown then cleared)
   - transport-error reset path (error shown then cleared)
4. Use valid project types and fixture-rendered DOM only (no `innerHTML` mutation shortcuts).

## Acceptance evidence

1. `git diff --check` clean.
2. Exact FE-03D gate exits `0`.
3. Diagnostic artifacts (`task-gate.json`, full log, manifests) all describe the same final gate execution.
4. SURGICAL returns `ACCEPT` for the LP result before task closure.

## Avoid repeating

Do not add fake type fields, invalid state literals, or synthetic tests that bypass the component’s rendered DOM contract.
