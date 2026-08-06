# LP code review (RUN_ID: 20260806T185629Z)

## Current evidence

- Active frontend task: `task-fe-03d-dom-state-tests` (`lp-runtime/progress.json`).
- Deterministic gate is red: exit `2` (`lp-runtime/gate_summary.md`).
- Codex correction packet is explicit and still unresolved (`lp-runtime/codex-qwen3-extra-instructions.md`).
- Working diff includes `frontend/src/app/features/rag/rag-page.component.spec.ts` and memory updates (`lp-git-status.txt`, `lp-git-diff-stat.txt`).

## First current defect

The first current defect is in `rag-page.component.spec.ts`: synthetic/invalid added tests and structure choices rejected by Codex `REVISE`, causing FE-03D gate failure and unproven DOM-behavior coverage.

## Bounded next action package

### Package: FE-03D-A-CORRECTION-01
- **Implementation level:** 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:**
  - `task-fe-03c-citations` accepted (already true in progress)
  - Apply Codex REVISE constraints exactly
- **allowed_paths:**
  - `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate:**
  - `git diff --check`
  - `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
  - Closure contract from `.opencode/task-plan.hierarchy.json`: `exact-gate-green + scope-clean + surgical-accept + controller-commit`
- **Required SURGICAL review:** Mandatory after LP pass before task closure.

## Required correction content (from evidence)

1. Remove rejected synthetic tests and direct `innerHTML` mutation patterns.
2. Restore one controlled-pending loading-state test using the existing Subject-based flow.
3. Split reset verification into two fixture-rendered tests:
   - success reset
   - transport-error reset
4. Keep existing valid answer/abstention/citation/error/escaping/service-isolation coverage intact.

## Acceptance evidence required

1. Changed paths remain within `rag-page.component.spec.ts` (plus controller-owned memory/progress files).
2. Hygiene gate `git diff --check` is clean.
3. FE-03D exact gate exits `0` with diagnostics, task-gate metadata, and local-understanding report consistent with that same final run.
4. SURGICAL returns `ACCEPT`.

## Avoid repeating

- Do not reintroduce fake response fields, invalid state types, unnecessary async helpers, or evidence bundles that do not match the final gate execution.
