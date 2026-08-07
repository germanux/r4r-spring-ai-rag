# Worker understanding check

## PC understanding status

Evidence indicates the backend worker completed a gate-green run but did not produce closure-complete metadata.

- Supporting evidence:
  - `pc-runtime/gate_summary.md` (green, exit 0)
  - `worker-requests/PC.json` (null `codex_decision`, `next_action`, `checkpoint_head`)
  - `pc-runtime/progress.json` (task-07 still `BLOCKED`)

### Required understanding for next pass

- This is a **Level 2 / PC** completion-quality pass, not a new feature pass.
- Keep to **task-07** with canonical `allowed_paths` only.
- Return coherent closure artifacts: gate outcome + row-count proof + non-null metadata.

## LP understanding status

Evidence indicates the frontend worker has a red gate and a detailed REVISE packet but no demonstrated successful follow-up run.

- Supporting evidence:
  - `lp-runtime/gate_summary.md` (exit 2)
  - `lp-runtime/codex-qwen3-extra-instructions.md` (explicit one-file corrective map)
  - `lp-git-status.txt` (single-file modification)
  - `lp-runtime/memory.md` (prior timeout; no acceptance claim)

### Required understanding for next pass

- This is a **Level 1 / LP** one-file correction in `rag-page.component.spec.ts`.
- Keep edits prescriptive and minimal; do not widen into production code.
- Prove FE-03D by DOM selectors/assertions via the exact gate.

## Shared constraints reaffirmed

1. No Git history operations by workers.
2. Run `git diff --check` before expensive deterministic gates.
3. Closure requires `exact-gate-green + scope-clean + controller-commit`.
