# PC code review (backend)

## Current authoritative evidence

- Active backend task: `task-06e-child-process` and still `PENDING`.
- Latest deterministic gate summary is green (`exit 0`).
- Current checkpoint for the task is `no-product-diff`.
- Worker request reason is `gate-green-no-checkpoint` with `codex_decision: null`.
- Latest Codex packet for this task remains `REVISE` and includes mandatory constraints (Spring initializer SPI + type-compatible service replacement).

Evidence:

- `pc-runtime/progress.json`
- `pc-runtime/gate_summary.md`
- `pc-runtime/checkpoint.json`
- `worker-requests/PC.json`
- `pc-runtime/codex-qwen3-extra-instructions.md`

## First current defect

The first blocking defect is **acceptance evidence gap**, not a confirmed gate failure: task 06e is still pending and there is no Codex `ACCEPT` recorded for the gate-green snapshot.

## Bounded next action for PC

Perform a single **review-focused** pass for `task-06e-child-process`:

1. Reconcile each mandatory item from `codex-qwen3-extra-instructions.md` against current backend test files.
2. Produce explicit requirement-to-file proof in local understanding.
3. Submit the existing gate-green snapshot for Codex decision.
4. Edit code only if a concrete mismatch is found during reconciliation.

## Acceptance conditions (must all hold)

1. `./scripts/task-gate.sh task-06e-child-process` remains `exit 0`.
2. Codex returns `ACCEPT` for `task-06e-child-process`.
3. Scope remains bounded to Codex packet targets (process IT/initializer/spring.factories/helper); no production script or production code changes.

## Avoid repeating

- Do **not** run another unchanged pass that ends again in `no-product-diff` and `codex_decision: null`.
- Do **not** reintroduce rejected approaches (`-Dcontext.initializer.classes`, incompatible replacement bean types).
