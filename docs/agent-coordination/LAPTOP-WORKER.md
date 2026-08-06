# LP code review — run 20260806T010642Z

## Current evidence read

- `lp-runtime/progress.json`: active task `task-fe-03c-citations` is `PENDING` with prior gate-green metadata only.
- `lp-runtime/codex-qwen3-extra-instructions.md`: Codex decision is `REVISE` with mandatory DOM-assertion corrections.
- `lp-git-status.txt`: dirty product file present: `frontend/src/app/features/rag/rag-page.component.spec.ts`.
- `lp-git-diff-stat.txt`: substantial spec-only delta (`108` inserted lines in the task-owned spec file).
- `lp-runtime/manifest.json`: no checkpoint or codex review artifact published for this run snapshot.

## First current defect (LP queue)

LP is still in an **unaccepted FE-03C revision state**. The unresolved defect is incomplete proof for FE-03C acceptance criteria unless the exact Codex-mandated rendered-DOM assertions are fully present and re-gated.

## Bounded next action package

- **Implementation level:** Level 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03c-citations` (work package `FE-03C-A`)
- **Dependencies:** `task-fe-03b-answer-abstention:ACCEPTED` (already satisfied)
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate:** `./scripts/frontend-task-gate.sh task-fe-03c-citations`
- **Required SURGICAL review:** mandatory after gate-green result

### One-pass instruction

Complete FE-03C-A exactly as constrained in Codex instructions:

1. DOM test for out-of-order structured citations rendered in correct ordinal order with correct source and full ordered heading-path segments.
2. DOM test proving `.citations-section` is absent for `{ abstained: false, citations: [] }`.
3. DOM test proving citation-like text in answer body is not parsed into citation DOM when structured `citations` is empty.

Then run `git diff --check`, run the exact frontend gate, and stop for SURGICAL review.

## Acceptance conditions

1. Diff remains within `frontend/src/app/features/rag/rag-page.component.spec.ts`.
2. `git diff --check` is clean.
3. `./scripts/frontend-task-gate.sh task-fe-03c-citations` exits `0`.
4. SURGICAL returns `ACCEPT` before closing `task-fe-03c-citations`.

## Avoid repeating

- Do **not** rely on generic green Angular runs without verifying FE-03C DOM-specific assertions.
- Do **not** submit partial citation assertions that miss order/path/empty-citation omission constraints.
