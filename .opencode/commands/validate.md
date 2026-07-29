Validate without editing product code:

1. `./scripts/task-gate.sh all`
2. `git diff --check`
3. `git status --short`

Report exact exits and the first failing condition. Do not use stale runtime logs as
proof.
