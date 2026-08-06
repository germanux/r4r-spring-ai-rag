## LP code review — run 20260806T145914Z

### Evidence reviewed
- `lp-runtime/progress.json`: active task `task-fe-03c-citations` is still `PENDING`.
- `lp-runtime/memory.md`: attempt 2, latest exact gate not run in this cycle, prior session timed out.
- `lp-runtime/codex-qwen3-extra-instructions.md`: Codex decision is `REVISE` with mandatory DOM-test additions.
- `lp-git-status.txt` and `lp-git-diff-stat.txt`: dirty task file `frontend/src/app/features/rag/rag-page.component.spec.ts` with substantial additions pending validation.

### First current defect
FE-03C has unresolved Codex REVISE requirements and no new gate evidence in this run, so current edits are unproven and cannot be routed for closure.

### Bounded next action package
- **Implementation level:** Level 1 (LP).
- **Assigned role:** LP.
- **Task ID:** `task-fe-03c-citations` (work package `FE-03C-A`, revise pass).
- **Dependencies:** `task-fe-03b-answer-abstention:ACCEPTED` (already satisfied).
- **allowed_paths:**
  - `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate:**
  - `git diff --check`
  - `./scripts/frontend-task-gate.sh task-fe-03c-citations`
- **Required SURGICAL review:** mandatory Codex `ACCEPT` after gate-green evidence.

### Required content in the LP pass (from current Codex packet)
1. Assert rendered citation ordering and ordinal/source/heading-path text from structured response fields when incoming citations are out of order.
2. Assert `.citations-section` is absent for a non-abstained success response with `citations: []`.
3. Assert citation-like text in `answer` is not parsed into citation DOM nodes when structured citations are empty.

### Avoid repeating
Do not run another long session without first implementing the explicit three-assertion REVISE checklist and producing fresh gate output.
