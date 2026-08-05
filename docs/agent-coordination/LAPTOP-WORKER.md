# LP code/task review (run 20260805T225504Z)

## Snapshot reviewed

- `lp-runtime/progress.json`: active task is `task-fe-03c-citations` and is still `PENDING`.
- `lp-runtime/codex-qwen3-extra-instructions.md`: Codex decision is `REVISE` with explicit missing FE-03C DOM assertions.
- `lp-runtime/memory.md`: still lists FE-03C behavior as unproven.
- `lp-git-diff-stat.txt`: only `.opencode/memory.frontend.md` is dirty; no task-owned frontend product file is changed in this snapshot.

## First current defect

The first unresolved defect is missing **task-specific rendered-DOM proof** for FE-03C in
`frontend/src/app/features/rag/rag-page.component.spec.ts`.

Specifically still required:

1. Ordered structured citations rendering assertion (order + ordinal + source + full heading path).
2. No citations section when response is success + non-abstained + `citations: []`.
3. No parsing of citation-like answer text into citation DOM when structured citations are empty.

## Bounded next action

Edit only `frontend/src/app/features/rag/rag-page.component.spec.ts` for those three assertions, then run:

1. `git diff --check`
2. `./scripts/frontend-task-gate.sh task-fe-03c-citations`

Stop at the first new failure and capture full diagnostics.

## Acceptance conditions

- `git diff --check` clean.
- Exact FE gate exits `0`.
- Codex returns `ACCEPT` for `task-fe-03c-citations`.

## Avoid repeating

- Do not treat generic green Angular runs as sufficient FE-03C proof.
- Do not run idle/no-edit cycles that skip the required DOM assertions.
