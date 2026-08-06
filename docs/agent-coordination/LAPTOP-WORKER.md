# LP code review (Ring)

## Current evidence-based status

- Active frontend task: `task-fe-03d-dom-state-tests` (`lp-runtime/progress.json`).
- Latest deterministic gate classification is failure with exit code `2` (`lp-runtime/gate_summary.md`).
- Codex already returned `REVISE` with a concrete correction packet (`lp-runtime/codex-qwen3-extra-instructions.md`).
- Current diff still contains a large unvalidated spec rewrite (`lp-git-diff-stat.txt`).

## First current defect (LP queue)

The current spec edits diverged from the bounded Codex correction packet; FE-03D remains red and unaccepted.

## Bounded next action package

- **Implementation level:** 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:**
  - `task-fe-03c-citations: ACCEPTED` (already satisfied)
  - Use active Codex `REVISE` packet as implementation authority for this pass
- **allowed_paths:**
  - `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate:**
  1. `git diff --check`
  2. `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
- **Required SURGICAL review:** mandatory after gate-green checkpoint (`ACCEPT` required for closure).

## Exact correction constraints for this pass

1. Remove defective additions identified by Codex (synthetic tests/state, manual loading mutation, innerHTML-based reset, invalid response shapes).
2. Implement only:
   - one controlled-pending loading test,
   - one success-reset test,
   - one transport-error-reset test,
   each using fixture-rendered DOM assertions.
3. Preserve existing valid answer/abstention/citation/escaping/service-isolation coverage.
4. Submit consistent diagnostics (`task-gate.json`, `gate-full.log`, manifest) from the same final run.

## Evidence paths

- `/home/german/Desarrollo/r4r-ring-agent.git/runtime/ring-agent/ring/20260806T194134Z/lp-runtime/gate_summary.md`
- `/home/german/Desarrollo/r4r-ring-agent.git/runtime/ring-agent/ring/20260806T194134Z/lp-runtime/codex-qwen3-extra-instructions.md`
- `/home/german/Desarrollo/r4r-ring-agent.git/runtime/ring-agent/ring/20260806T194134Z/lp-git-status.txt`
- `/home/german/Desarrollo/r4r-ring-agent.git/runtime/ring-agent/ring/20260806T194134Z/lp-git-diff-stat.txt`
- `/home/german/Desarrollo/r4r-ring-agent.git/runtime/ring-agent/ring/20260806T194134Z/lp-runtime/progress.json`
