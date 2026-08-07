# LP code review (Ring)

## Evidence reviewed (current RUN_DIR)

- `runtime/ring-agent/ring/20260807T005523Z/lp-runtime/progress.json`
- `runtime/ring-agent/ring/20260807T005523Z/lp-runtime/memory.md`
- `runtime/ring-agent/ring/20260807T005523Z/lp-runtime/codex_plan.json`
- `runtime/ring-agent/ring/20260807T005523Z/lp-runtime/codex-qwen3-extra-instructions.md`
- `runtime/ring-agent/ring/20260807T005523Z/lp-runtime/gate_summary.md`
- `runtime/ring-agent/ring/20260807T005523Z/lp-git-status.txt`
- `runtime/ring-agent/ring/20260807T005523Z/lp-git-diff-stat.txt`

## Current diagnosis

1. Active LP task is `task-fe-03d-dom-state-tests`.
2. There is an in-progress diff only in `frontend/src/app/features/rag/rag-page.component.spec.ts`.
3. Latest deterministic evidence is a gate failure (`exit 2`) plus Codex `REVISE` instructions that identify concrete local defects: formatting/syntax damage and prohibited patterns (manual state mutation, guessed selectors, invalid response shapes).

## First current defect to correct

**Defect class:** local test-file correction quality and rule compliance.

The current patch must be repaired to satisfy FE-03D requirements with valid DOM-driven tests and clean formatting.

## Bounded next package

- **Implementation level:** Level 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** `task-fe-03c-citations:ACCEPTED` (already satisfied)
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate:**
  1. `git diff --check`
  2. `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`

## Prescribed one-pass correction

1. Restore valid pre-attempt suite structure (balanced braces/indentation; no displaced existing tests).
2. Remove rejected patterns called out by Codex (`innerHTML` mutation, manual loading flags, guessed selectors, invalid shapes, unnecessary `of/tick`).
3. Add exactly these FE-03D DOM checks:
   - controlled-pending loading + duplicate-submit suppression test,
   - independent success-reset test with citations,
   - independent transport-error-reset test with a fresh Subject.

## Acceptance evidence required

- Scoped non-empty patch only in the spec file.
- `git diff --check` clean.
- FE-03D task gate green once with consistent diagnostics and understanding notes.
- Policy closure remains controller-owned (exact-gate-green + scope-clean + controller-commit).

## Avoid repeating

- Do **not** reintroduce previously rejected synthetic/manual patterns.
- Do **not** submit another pass with formatting/trailing-whitespace regressions.
