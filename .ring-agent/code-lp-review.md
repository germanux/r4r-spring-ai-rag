# LP code review (frontend)

## Current evidence

- Active task: `task-fe-03d-dom-state-tests` (`lp-runtime/progress.json`).
- Deterministic gate is failing with exit `2` (`lp-runtime/gate_summary.md`).
- Codex decision is `REVISE` with explicit file-local instructions (`lp-runtime/codex_plan.json`, `lp-runtime/codex-qwen3-extra-instructions.md`).
- Changed scope is a single test file: `frontend/src/app/features/rag/rag-page.component.spec.ts` (`worker-requests/LP.json`).

## First current defect

The first defect is a **test-file quality and instruction-compliance regression** in `rag-page.component.spec.ts` (malformed additions, prohibited patterns, and whitespace/gate failure), already classified by Codex.

## Bounded next action package

- **Implementation level:** 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:**
  - Active Codex REVISE packet is authoritative
  - No backend dependency for this file-local correction
- **allowed_paths:**
  - `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate:**
  - `git diff --check`
  - `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`
- **Required SURGICAL review:** mandatory after LP pass; closure requires SURGICAL `ACCEPT` and controller commit.

## Execution constraints for LP pass

1. Restore valid suite structure and remove prohibited synthetic/manually-mutated patterns listed in Codex instructions.
2. Add only the three prescribed DOM tests (controlled pending loading, success reset, transport-error reset).
3. Preserve existing valid answer/abstention/citation/escaping/service-isolation coverage.
4. Keep two-space indentation, balanced blocks, valid response shapes, and no trailing whitespace.

## Acceptance evidence required in next cycle

1. `git diff --check` passes.
2. FE-03D exact gate exits `0`.
3. Local understanding report maps each requirement to concrete selectors/assertions.
4. SURGICAL review returns `ACCEPT` for closure.

## Avoid repeating

Do not reintroduce innerHTML mutation, manual loading-state mutation, guessed selectors, invalid response shapes, or ad hoc test rewrites already rejected by Codex.
