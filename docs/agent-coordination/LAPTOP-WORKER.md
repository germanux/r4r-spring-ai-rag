# LP code review — RUN 20260805T234824Z

## Current evidence read

- `lp-runtime/progress.json`: active task `task-fe-03c-citations` is still `PENDING`.
- `lp-runtime/codex-qwen3-extra-instructions.md`: Codex decision is `REVISE` with mandatory FE-03C rendered-DOM instructions.
- `lp-git-status.txt`: dirty paths include `frontend/src/app/features/rag/rag-page.component.spec.ts`.
- `lp-git-diff-stat.txt`: spec file has large unreviewed delta (`+108` lines) and no closure evidence.
- `lp-runtime/manifest.json`: no checkpoint/codex review artifact is present in this snapshot.

## First current defect (LP)

LP has not yet produced reviewer-verified FE-03C coverage for all required rendered-DOM citation behaviors. The current dirty spec diff is not yet proven against the exact FE-03C contract.

## Bounded next action package

- **Implementation level:** 1 (LP)
- **Assigned role:** LP (execution), SURGICAL Codex (mandatory reviewer)
- **Task ID:** `task-fe-03c-citations`
- **Work package:** `FE-03C-A`
- **Dependencies:** `task-fe-03b-answer-abstention:ACCEPTED`
- **allowed_paths:**
  - `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate:** `./scripts/frontend-task-gate.sh task-fe-03c-citations`
- **Required SURGICAL review:** `ACCEPT` required before controller closure

### One-pass directive

Implement the Codex mandatory set in one bounded pass inside `rag-page.component.spec.ts`:

1. Render a success response with out-of-order structured citations; assert rendered citation order and per-item ordinal/source/multi-segment heading path.
2. Render `{ abstained: false, citations: [] }`; assert `.citations-section` is absent.
3. Render citation-like answer text with empty structured citations; assert text stays in `.answer-content` and no `.citation-item`/`.citations-section` is created.
4. Run `git diff --check` then run exact gate; submit resulting evidence for SURGICAL review.

## Avoid repeating

- Do not treat generic green Angular runs as sufficient.
- Do not rely on non-DOM shortcuts (e.g., only checking component internals/getters) for FE-03C acceptance.
