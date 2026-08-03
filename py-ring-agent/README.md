# R4R Ring Agent — phase 2.3

Everything introduced here lives under `py-ring-agent/`. Runtime evidence remains
under the Ring repository's ignored `runtime/` tree.

## Fixed worktree names

The launchers use these defaults at the top of each root script:

```text
~/Desarrollo/r4r-ring-agent.git
~/Desarrollo/r4r-pc-worker.git
~/Desarrollo/r4r-lp-worker.git
```

Edit those variables directly when the development root differs. No required
command-line parameters are used.

## Prepare the short worker paths

The migration script preserves the current branches and worktrees:

```bash
./py-ring-agent/prepare-worker-worktrees.py
```

By default it moves:

```text
r4r-spring-ai-rag.git              -> r4r-pc-worker.git
r4r-spring-ai-rag-laptop-agent.git -> r4r-lp-worker.git
```

If an old path is absent, it creates the corresponding linked worktree from:

```text
agent/pc-qwen3-worker
agent/laptop-qwen3-worker
```

Do not run PC or LP while moving their worktree directories.

## Entry points

- `run-ring-agent.py`: runs The Ring from `r4r-ring-agent.git` and captures Git
  evidence from the Ring, PC and LP worktrees before each fresh session.
- `run-worker-streamed.py`: runs the PC or LP controller inside its own worktree;
  logs and operator control remain centralized in the Ring worktree.
- `run-harness-maintainer.py`: repairs only the Ring harness inside an isolated
  detached temporary worktree.
- `prepare-worker-worktrees.py`: moves or creates the two short worker paths.

## Run

```bash
chmod +x py-ring-agent/*.py

./py-ring-agent/prepare-worker-worktrees.py
./py-ring-agent/run-ring-agent.py
./py-ring-agent/run-worker-streamed.py PC
./py-ring-agent/run-worker-streamed.py LP
./py-ring-agent/run-harness-maintainer.py --once
```

`run-worker-streamed.py` keeps the optional `PC`/`LP` argument as a shortcut. You
can instead edit `DESTINATION` at the top and run it without arguments.

## Layout

```text
r4r-ring-agent.git/
├── py-ring-agent/
├── .ring-agent/
└── runtime/ring-agent/
    ├── ring/
    ├── pc/
    ├── lp/
    └── maintenance/

r4r-pc-worker.git/
└── backend worker and its own controller

r4r-lp-worker.git/
└── frontend worker and its own controller
```

## Operator command file

All processes share the Ring file:

```text
r4r-ring-agent.git/runtime/the-ring-command.jsonc
```

Set `next_state` to `stop`, `pause`, `continue` or `restart`, and `target` to
`RING`, `PC`, `LP`, `MAINTAINER` or `ALL`.

## Tests

```bash
PYTHONPATH=py-ring-agent/src \
python3 -m unittest discover -s py-ring-agent/tests -p 'test_*.py'
```

## Worktree rename recovery (phase 2.4)

`prepare-worker-worktrees.py` now repairs all linked worktree `.git` pointers when
the primary checkout has been renamed. It is safe to rerun after a partially
completed phase 2.3 migration.

## The-Ring ↔ Qwen3 exchange (phase 2.25)

`run-ring-system.py` now supervises three long-lived processes: the deterministic
PC/LP guardian and the separately locked cognitive `run-ring-agent.py` loop.
Every Ring cycle copies a bounded snapshot of the latest PC and LP controller
artifacts into its evidence directory, then writes one advisory JSON directive per
worker:

```text
runtime/control/PC/ring-qwen3-directive.json
runtime/control/LP/ring-qwen3-directive.json
```

The worker controller accepts a directive only when its schema, target, active task,
timestamp and `priority=advisory` are valid. It injects the accepted directive into
Qwen3 pre-edit, edit and assimilation prompts and into the next Codex plan/review
context. The exact task, deterministic gate and current Codex correction packet
always override The-Ring.

The default review interval is one hour and can be changed with:

```bash
export R4R_RING_REVIEW_INTERVAL_SECONDS=3600
export R4R_RING_DIRECTIVE_MAX_AGE_SECONDS=10800
```

## Event-triggered worker reviews

PC and LP controllers request a fresh cognitive Ring review after a gate-green
checkpoint and after a Codex decision. Requests are written atomically under
`runtime/control/RING/requests/`, consumed once, and preserved with worker memory and
checkpoint evidence in the next Ring run. The hourly review remains the fallback and
all Ring directives remain advisory.
