Validate the current repository without editing application code:

1. `./scripts/verify.sh unit`
2. `./scripts/verify.sh all`
3. `git diff --check`
4. `git status --short`

Report exact exits and the first failing condition. Do not declare success from stale logs.
