# LP code review (frontend)

## Current authoritative evidence

- Active frontend task: `task-fe-01-angular17-bootstrap` and still `PENDING`.
- Latest deterministic gate summary is green (`exit 0`), but last checkpoint is `no-product-diff`.
- Latest Codex decision for FE-01 is `REVISE` with explicit correction: ensure production selects `environment.prod.ts`.
- Snapshot of LP worktree state shows `frontend/angular.json` modified (`7 insertions, 1 deletion`).
- Local understanding report is explicitly flagged as insufficient by Codex (missing requirement-to-file mapping).

Evidence:

- `lp-runtime/progress.json`
- `lp-runtime/codex-qwen3-extra-instructions.md`
- `lp-runtime/checkpoint.json`
- `lp-runtime/local_understanding.md`
- `lp-git-status.txt`
- `lp-git-diff-stat.txt`

## First current defect

The first defect is **unclosed Codex REVISE correction for production environment selection**, compounded by evidence quality issues (missing requirement-to-file mapping). There is likely an in-progress `frontend/angular.json` change, but this snapshot does not yet prove gate + Codex acceptance on that exact change.

## Bounded next action for LP

Execute one focused correction pass on `task-fe-01-angular17-bootstrap`:

1. Finalize `frontend/angular.json` so production build selects `src/environments/environment.prod.ts` while preserving development replacement.
2. Run `git diff --check`.
3. Run exactly `./scripts/frontend-task-gate.sh task-fe-01-angular17-bootstrap`.
4. Produce a local understanding report that maps each FE-01 requirement to concrete files.

## Acceptance conditions (must all hold)

1. Frontend exact gate returns `exit 0` after the intended config change.
2. Codex returns `ACCEPT` for `task-fe-01-angular17-bootstrap`.
3. Scope remains frontend-only and Angular major remains 17.

## Avoid repeating

- Do **not** submit another `no-product-diff`/no-mapping pass.
- Do **not** widen scope into unrelated RAG UI/client work before FE-01 is accepted.
