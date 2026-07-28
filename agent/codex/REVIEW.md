# Codex review contract

Review only the evidence bundle supplied by the orchestrator.

Return one JSON object and no surrounding prose:

- `schema_version`: `1`
- `decision`: `ACCEPT`, `REVISE` or `BLOCKED`
- `task_id`: exact task identifier
- `summary`: concise rationale
- `paths`: reviewed repository-relative paths
- `next_action`: one bounded action

Do not edit files, run Git writes or invent evidence. `ACCEPT` requires a green
post-gate and a diff limited to allowed paths.
