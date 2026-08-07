# R4R Ring stabilization patch

This patch stabilizes the current Ring execution before Phase 3. It does not launch the
PC or LP workers and it does not modify their worktrees.

## Guarantees

- `r4r-ring` must exist as an OpenCode `primary` agent.
- The configured model must be visible in the OpenCode model catalogue.
- `external_directory: deny` remains enforced.
- Python copies bounded RING/PC/LP evidence into one run-scoped snapshot.
- The model may read only that `RUN_DIR` and may write only staged files below
  `RUN_DIR/output`.
- Repeated access to a forbidden PC/LP worktree is cut after two equivalent attempts.
- Repeated tool/schema errors are cut after three equivalent attempts.
- Total timeout is 90 minutes; idle-output timeout is 15 minutes.
- Ctrl+C or a fresh `runtime/the-ring-command.jsonc` stop command produces
  `OPERATOR_INTERRUPTED`.
- Final artifacts are validated before atomic promotion.
- No Git add/commit/reset/clean/checkout/push operation is performed.

## Terminal statuses

- `SUCCESS`
- `PARTIAL_ARTIFACTS`
- `INVALID_AGENT`
- `REPEATED_TOOL_ERROR`
- `INVALID_FINAL_ARTIFACT`
- `TIMEOUT`
- `OPERATOR_INTERRUPTED`

Every run writes:

`runtime/ring-agent/ring/<RUN-ID>/supervisor-result.json`

## Installation

From the Ring repository root, after extracting this package over the repository:

```bash
./scripts/install-ring-stabilization.sh
```

The installer preserves the previous `ring_loop.py` as
`ring_loop_legacy.py`, creates an additional timestamped backup, compiles the Python
package, runs the focused tests, and verifies `r4r-ring (primary)`.

## One-shot execution

```bash
./py-ring-agent/run-ring-stabilized.py --once
```

The existing Python call to `run_ring_loop(paths, once=True)` also becomes stabilized
after installation through the compatibility wrapper.

## Isolated LP path

Ring must inspect the same LP path that is actually active:

```bash
./py-ring-agent/run-ring-stabilized.py \
  --once \
  --lp-worktree "$HOME/Desarrollo/r4r-lp-worker.git"
```

The launcher rejects a path whose branch is not `agent/laptop-qwen3-worker`.

## Strict result rule

Operational `.ring-agent/*` and `.opencode/current/ring/worker-understanding.md` files
are promoted locally only when the status is `SUCCESS`; they are regenerated and
ignored by Git. Durable `.ring-agent/evidence/<task>/...` is written only for a new
semantic attempt. Partial staged artifacts remain in the run directory for diagnosis.
