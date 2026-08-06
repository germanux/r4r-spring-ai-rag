# LP code review — RUN 20260806T003326Z

## Evidence reviewed

- `lp-runtime/progress.json` → active task `task-fe-03c-citations` remains `PENDING`.
- `lp-runtime/codex-qwen3-extra-instructions.md` → Codex decision is `REVISE`, with mandatory FE-03C rendered-DOM assertions.
- `lp-git-status.txt` → dirty task-owned file: `frontend/src/app/features/rag/rag-page.component.spec.ts`.
- `lp-git-diff-stat.txt` → sizable unaccepted spec diff (`108` insertions in spec file).
- `lp-runtime/memory.md` → latest exact gate not run in this LP run, no checkpoint recorded.

## First current defect (LP)

The FE-03C correction is in-flight but unproven: LP has an unaccepted spec diff and an outstanding Codex `REVISE` mandate for missing citation-contract DOM proof. Current evidence does not show a completed revise pass (`git diff --check` + exact gate + SURGICAL review outcome) for this attempt.

## Bounded next action package

### Action FE-03C-A-REV2

- **Implementation level:** Level 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03c-citations` (work package `FE-03C-A`)
- **Dependencies:**
  - `task-fe-03b-answer-abstention:ACCEPTED` (already satisfied)
  - Codex `REVISE` packet must be fully resolved in this pass.
- **allowed_paths:**
  - `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate / constraint:**
  - Preflight: `git diff --check`
  - Exact gate: `./scripts/frontend-task-gate.sh task-fe-03c-citations`
  - Assertions must prove: ordered structured citations, omitted citation section for empty citations, and no parsing of citation-like text from answer body.
- **Required SURGICAL review:** Yes (mandatory before closure).
- **Acceptance evidence required:**
  - Clean preflight output.
  - Exact gate exit `0` for `task-fe-03c-citations`.
  - SURGICAL Codex decision `ACCEPT`.

## Do not repeat

- Do **not** rely on generic Angular green runs as FE-03C completion evidence.
- Do **not** add or modify component/template files unless a failing focused test proves a real component defect.
