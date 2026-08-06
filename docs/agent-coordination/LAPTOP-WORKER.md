# LP code review (run 20260806T185129Z)

## Current evidence snapshot

- Active task: `task-fe-03d-dom-state-tests` (`lp-runtime/progress.json`).
- Deterministic gate is red (`exit 2`) (`lp-runtime/gate_summary.md`).
- Codex disposition is `REVISE` with explicit selector-level repair instructions (`lp-runtime/memory.md`, `lp-runtime/codex-qwen3-extra-instructions.md`).
- Current LP diff includes `.opencode/memory.frontend.md` plus `frontend/src/app/features/rag/rag-page.component.spec.ts` (`lp-git-status.txt`, `lp-git-diff-stat.txt`).

## First current defect (LP queue)

The active spec patch introduced defective synthetic test patterns and failed the exact gate. The correction packet explicitly requires replacing those additions with controlled pending/loading assertions and two separate reset tests using fixture-rendered DOM.

## Bounded next package

### Package ID: FE-03D-A-LP-REVISE-01
- **Implementation level:** 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:**
  - `task-fe-03c-citations:ACCEPTED` (already satisfied in progress)
  - Current Codex correction packet (`lp-runtime/codex-qwen3-extra-instructions.md`)
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate:** `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
- **Required SURGICAL review:** Yes, mandatory before closure.

### Exact work content required in this single pass
1. Remove newly added synthetic success/abstention and synthetic `innerHTML` reset tests.
2. Restore one controlled pending observable loading-state test asserting:
   - `.loading-state[role="status"]` contains loading text,
   - rendered `textarea` and `.submit-button` are disabled,
   - duplicate submit while pending does not increase service calls beyond one.
3. Split reset behavior into two independent tests:
   - success reset clears answer/citations/error and restores idle state,
   - transport-error reset clears error alert and restores idle state.
4. Keep existing valid coverage intact; use valid project types only.
5. Run `git diff --check` before the exact gate.

### Acceptance evidence required from this pass
1. Non-empty scoped diff only in the allowed spec path.
2. `git diff --check` clean.
3. Exact FE-03D gate exit `0` from the same final run represented in task-gate and diagnostics artifacts.
4. SURGICAL Codex review returns `ACCEPT` before closure.

## Do-not-repeat guard

- Do not submit synthetic type-invalid tests.
- Do not mutate DOM via `nativeElement.innerHTML`.
- Do not produce mismatched evidence artifacts from different gate executions.
