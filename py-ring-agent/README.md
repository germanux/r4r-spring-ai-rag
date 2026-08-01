# R4R Ring Agent — phases 1 and 2

Everything introduced by these phases lives under `py-ring-agent/`.
The runtime control file, logs and generated maintenance reports are created only
when the programs run.

## Entry points

- `run-ring-agent.py`: launches fresh OpenCode Ring analysis sessions.
- `run-worker-streamed.py`: launches PC or LP with live persistent console streaming.
- `run-harness-maintainer.py`: every four hours launches a fresh bounded OpenCode
  maintainer session in an isolated detached Git worktree.

## Harness maintainer policy

The editable defaults are near the top of:

```text
src/r4r_ring_agent/harness_maintainer.py
```

Default policy:

- one defect per pass;
- maximum three changed files;
- maximum 120 changed lines, additions plus deletions;
- maximum two fresh sessions: initial attempt plus one self-correction;
- no Java or frontend product changes;
- no package installation or Git history changes;
- candidate is never applied to the active worktree;
- candidate patch and analysis are written under `.ring-agent/maintenance/<run-id>/`;
- full streamed console logs are written under `runtime/ring-maintainer/<run-id>/`.

The active worktree may contain product changes. The maintainer only blocks when an
allowed harness path is already dirty, because it must compare its candidate against
an unambiguous committed baseline.

## Run

```bash
chmod +x \
  py-ring-agent/run-ring-agent.py \
  py-ring-agent/run-worker-streamed.py \
  py-ring-agent/run-harness-maintainer.py

./py-ring-agent/run-ring-agent.py
./py-ring-agent/run-worker-streamed.py PC
./py-ring-agent/run-worker-streamed.py LP
./py-ring-agent/run-harness-maintainer.py
```

One maintenance pass for validation:

```bash
./py-ring-agent/run-harness-maintainer.py --once
```

## Operator command file

The processes share:

```text
runtime/the-ring-command.jsonc
```

Set `next_state` to `stop`, `pause`, `continue` or `restart`, and set `target`
to `RING`, `PC`, `LP`, `MAINTAINER` or `ALL`.

## Tests

```bash
PYTHONPATH=py-ring-agent/src \
python3 -m unittest discover -s py-ring-agent/tests -p 'test_*.py'
```


## Phase 2.1 hotfix

- `target: "RING"` now controls both the Ring loop and the harness maintainer.
- The maintainer agent explicitly allows source reads while still denying real `.env` files.
- Edits remain restricted to the bounded harness paths and are validated externally.
