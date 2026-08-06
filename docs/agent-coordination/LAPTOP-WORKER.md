# LP code review (evidence cycle 20260806T193132Z)

## Current verdict
- **Queue status:** continue with one tightly bounded correction pass.
- **Active task:** `task-fe-03d-dom-state-tests`.
- **First current defect:** LP attempt remains red (`exit=2`) and Codex marked it `REVISE` due to defective/synthetic test additions and requirement mismatch.

## Evidence reviewed
- `runtime/ring-agent/ring/20260806T193132Z/worker-requests/LP.json` (Codex `REVISE`, explicit corrective next action)
- `runtime/ring-agent/ring/20260806T193132Z/lp-runtime/memory.md` (enumerated defects and required replacement tests)
- `runtime/ring-agent/ring/20260806T193132Z/lp-runtime/codex-qwen3-extra-instructions.md` (mandatory bounded correction packet)
- `runtime/ring-agent/ring/20260806T193132Z/lp-runtime/gate_summary.md` (deterministic gate failure)
- `runtime/ring-agent/ring/20260806T193132Z/lp-runtime/controller_state.json` (`GLOBAL_ATTEMPT_LIMIT_REACHED`)

## Bounded work package
- **Implementation level:** **Level 1**
- **Assigned role:** **LP**
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:**
  - Prior accepted frontend baseline through `task-fe-03c-citations`
  - Active Codex correction packet for attempt-06
- **allowed_paths (task authority):** from `.opencode/task-plan.frontend.json` = `frontend/**`, `docs/frontend/**`
- **allowed_paths (this pass, stricter):** `frontend/src/app/features/rag/rag-page.component.spec.ts` only
- **Exact gate:** `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
- **Pre-gate guard:** `git diff --check`

## Required implementation in this single pass
1. Remove defective additions called out by Codex (synthetic tests, malformed loading fragment, invalid state mutations, unnecessary async helpers).
2. Rebuild one controlled-pending loading test with selector-based DOM assertions and duplicate-submit call-count protection.
3. Add two independent reset tests:
   - success-path reset (answer/citations present before clear, absent after clear)
   - transport-error reset (alert present before clear, absent after clear)
4. Keep existing valid answer/abstention/citation/escaping/service-isolation coverage unchanged.

## Acceptance conditions
- Non-empty scoped patch limited to the single spec file above.
- `git diff --check` passes.
- Exact FE-03D gate exits `0` with consistent diagnostics.
- SURGICAL Codex review returns `ACCEPT` before closure.

## Avoid repeating
- Reintroducing synthetic DOM mutations or invalid response shapes.
- Producing mismatched gate artifacts/local-understanding that do not describe the same final run.

## Required SURGICAL review for closure
Mandatory after the pass; LP cannot self-close even if gate turns green.
