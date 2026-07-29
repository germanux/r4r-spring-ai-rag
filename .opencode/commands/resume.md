Resume the automatic task cycle.

1. Read `AGENTS.md`, `.opencode/commands/task.md` and `.opencode/memory.md`.
2. Read only the task referenced by `runtime/locks/active-task.json` when that file
   exists; otherwise use the first pending task in `.opencode/progress.json`.
3. Do not select or mark a later task yourself.
4. Do not run Git writes.
5. Finish at the selected task gate and report exact evidence.

Normal continuation should use `./scripts/run-codex-agent.sh`; this command exists
only for manual inspection or recovery.
