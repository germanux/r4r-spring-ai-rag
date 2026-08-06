# LP code review (Ring)

## Current evidence verdict

- Active task: `task-fe-03d-dom-state-tests`.
- Deterministic gate status: **failed** (`exit 2`, `gate-failure`).
- Codex status: **REVISE** with explicit correction packet.
- First current defect: the spec includes defective synthetic tests and inconsistent understanding/evidence packaging versus the active REVISE instructions.

Evidence:

- `/home/german/Desarrollo/r4r-ring-agent.git/runtime/ring-agent/ring/20260806T190630Z/lp-runtime/gate_summary.md`
- `/home/german/Desarrollo/r4r-ring-agent.git/runtime/ring-agent/ring/20260806T190630Z/lp-runtime/memory.md`
- `/home/german/Desarrollo/r4r-ring-agent.git/runtime/ring-agent/ring/20260806T190630Z/lp-runtime/codex-qwen3-extra-instructions.md`

## Bounded action package

### PKG-LP-FE03D-SPEC-REPAIR

- **Implementation level:** 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:**
  - Active Codex REVISE packet must be followed exactly.
  - Existing accepted predecessor task: `task-fe-03c-citations`.
- **allowed_paths:**
  - `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate:**
  - `git diff --check`
  - `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
- **Required SURGICAL review:** yes, mandatory before closure (`ACCEPT` required).

## Prescribed one-pass correction focus

1. Remove defective synthetic additions called out by Codex.
2. Restore one controlled pending-observable loading-state test with DOM selector assertions:
   - `.loading-state[role="status"]`
   - rendered `textarea`
   - `.submit-button`
   - assert single service call even after one extra `onSubmit()` during pending state.
3. Split reset behavior into two independent fixture-rendered tests:
   - success-reset path (answer/citations present before clear; absent after clear; idle present),
   - transport-error reset path (error alert present before clear; absent after clear; idle present).
4. Publish internally consistent evidence from the same final gate execution.

## Avoid repeating

- Do not add fake response fields, invalid state values, direct `innerHTML` mutations, or unnecessary timing helpers.
- Do not submit mismatched diagnostics (`task-gate.json`, manifest, and full log must refer to the same run).
