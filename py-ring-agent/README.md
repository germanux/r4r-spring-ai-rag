# R4R Ring Agent — phase 1

Everything introduced by this phase lives under `py-ring-agent/`.
Nothing is installed under the repository root `scripts/` directory or under
`py-codex-agent/`.

## Editable entry points

- `run-ring-agent.py`: launches a fresh OpenCode Ring session.
- `run-worker-streamed.py`: launches PC or LP while mirroring the live console to disk.

The main defaults are located near the top of those files and in:

- `src/r4r_ring_agent/ring_loop.py`
- `src/r4r_ring_agent/ring_process.py`

## Run

From the repository root:

```bash
chmod +x py-ring-agent/run-ring-agent.py py-ring-agent/run-worker-streamed.py

./py-ring-agent/run-ring-agent.py
./py-ring-agent/run-worker-streamed.py PC
./py-ring-agent/run-worker-streamed.py LP
```

## Operator command file

The processes share:

```text
runtime/the-ring-command.jsonc
```

Set `next_state` to `stop`, `pause`, `continue` or `restart`, and set `target`
to `RING`, `PC`, `LP` or `ALL`.

## Tests

```bash
PYTHONPATH=py-ring-agent/src \
python3 -m unittest discover -s py-ring-agent/tests -p 'test_*.py'
```
