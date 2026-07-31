# Resume

Use `./scripts/run-codex-agent.sh`. The active lock is authoritative. The controller
may adopt commits whose paths are entirely inside the active task; it must reject any
out-of-scope commit.

Before manual recovery, inspect only `./scripts/run-codex-agent.sh --status`,
`.opencode/progress.json` and the latest `state.json`. Do not reset, clean or
commit merely to satisfy the lock.
