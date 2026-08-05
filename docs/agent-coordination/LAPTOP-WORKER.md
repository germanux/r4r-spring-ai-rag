# LP code review (Ring)

## Current diagnosis

First current defect for the LP queue is **unfinished FE-03C Codex REVISE obligations** (rendered-DOM citation proof), with no SURGICAL acceptance evidence yet.

Evidence:
- `lp-runtime/codex-qwen3-extra-instructions.md` is explicit: Codex decision is `REVISE` and mandates three specific rendered-DOM test behaviors.
- `lp-runtime/progress.json` still marks `task-fe-03c-citations` as `PENDING`.
- `lp-git-status.txt` shows an active product diff in `frontend/src/app/features/rag/rag-page.component.spec.ts`.
- `lp-git-diff-stat.txt` shows a large in-flight test edit (`108` insertions) without acceptance evidence.

## Routed package

- **Implementation level:** Level 1 (LP).
- **Assigned role:** LP.
- **Task ID:** `task-fe-03c-citations` (work package `FE-03C-A`).
- **Dependencies:** `task-fe-03b-answer-abstention:ACCEPTED` (satisfied).
- **allowed_paths:** `frontend/src/app/features/rag/rag-page.component.spec.ts` only.
- **Exact gate:** `./scripts/frontend-task-gate.sh task-fe-03c-citations`.
- **Required SURGICAL review:** **Yes (mandatory)** before closure.

## One-pass next action

Execute one bounded FE-03C-A pass in the spec file only:
1. Ensure rendered-DOM assertions cover: ordered citations (ordinal/source/heading path), omission of citations section for empty citation arrays, and non-parsing of citation-like answer text.
2. Run `git diff --check`.
3. Run the exact gate `./scripts/frontend-task-gate.sh task-fe-03c-citations`.
4. Stop and hand off evidence for SURGICAL Codex review.

## Acceptance conditions

- Only the allowed spec path is changed.
- `git diff --check` is clean.
- Exact FE gate is green.
- Codex decision becomes `ACCEPT` for `task-fe-03c-citations`.

## Avoid repeating

- Do **not** treat generic Angular success as task completion when FE-03C rendered-DOM assertions are incomplete.
