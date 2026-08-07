# Resume one OpenCode assignment

Use `./scripts/run-opencode-worker.sh --destination PC` or
`./scripts/run-opencode-worker.sh --destination LP`.

A resume is valid only when the worker has a fresh Ring assignment for the same task.
Inspect the assignment, the worker's local progress and the latest controller
`state.json`. Do not reset, clean, commit or select a different task merely to resume.
A blocked task requires an unconsumed `RETRY_AUTHORIZED` assignment.
