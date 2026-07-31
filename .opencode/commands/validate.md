# Validate

Run without product edits:

1. `./scripts/task-gate.sh all`
2. `git diff --check`
3. `git status --short`

Report exact exits, test totals and the first failing condition. Current output only;
stale logs are not proof.
