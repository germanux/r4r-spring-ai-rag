# LP Code Review (Ring)

## Current evidence-backed defect

Task `task-fe-03d-dom-state-tests` remains unresolved.

- `lp-runtime/codex-qwen3-extra-instructions.md` is `REVISE` with a strict one-file repair packet.
- `lp-runtime/memory.md` records a prior timeout (`session-timeout`) and no new acceptance.
- `lp-git-status.txt` and `lp-git-diff-stat.txt` show an uncommitted diff in `frontend/src/app/features/rag/rag-page.component.spec.ts`.
- No LP controller-state or gate-summary artifact is present in this RUN_DIR to prove a fresh pass.

The first current defect is still the **single-file FE-03D test correction**.

## Bounded next work package

- **Implementation level:** Level 1
- **Assigned role:** LP
- **Task ID:** `task-fe-03d-dom-state-tests`
- **Dependencies:** `task-fe-03c-citations:ACCEPTED`
- **allowed_paths (canonical):** `frontend/**`, `docs/frontend/**`
- **Bounded write target for this pass:** `frontend/src/app/features/rag/rag-page.component.spec.ts`
- **Exact gate:**
  - `git diff --check`
  - `./scripts/frontend-task-gate.sh task-fe-03d-dom-state-tests`

## Acceptance conditions for this pass

1. Only the prescribed FE-03D correction packet behavior is implemented in the one spec file.
2. Test structure is valid (balanced braces/indentation, no malformed additions called out by Codex).
3. Deterministic FE-03D gate exits `0`.
4. Scope is clean and controller can close with commit.

## Avoid repeating

Do **not** reintroduce internal-state mutations, guessed selectors, malformed spec structure, or timeout-prone reruns without first applying the correction packet.
