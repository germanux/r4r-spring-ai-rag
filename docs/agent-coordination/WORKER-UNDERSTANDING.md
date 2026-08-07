# Worker Understanding Check

## PC understanding quality

Evidence indicates PC has a valid technical implementation path for task-07 but is blocked at closure mechanics:

- Gate success is proven (`pc-runtime/gate_summary.md`).
- Closure completion is not proven due to `CHECKPOINT_COMMIT_FAILED` (`pc-runtime/controller_state.json`).

Required understanding for the next pass: this is a **closure-quality** correction, not feature expansion. Keep task and scope fixed, produce complete closure metadata, and preserve deterministic row-count proof.

## LP understanding quality

Codex explicitly flagged LP understanding as inadequate in the last revise packet (`lp-runtime/codex-qwen3-extra-instructions.md`), and prior execution timed out (`lp-runtime/memory.md`).

Required understanding for the next pass: implement only the three prescribed FE-03D DOM-state tests in the single spec file while preserving existing valid coverage and structure.

## Directive precision for both workers

- Enforce one focused pass per worker.
- Run `git diff --check` before expensive gates.
- No scope expansion, no Git history commands, no gate bypass.
- Closure target remains: exact-gate-green + scope-clean + controller-commit.
