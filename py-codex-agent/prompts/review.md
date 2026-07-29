# Codex review contract

Review exactly one selected OpenCode implementation in read-only mode. Inspect the
current repository, selected task, changed paths, diff/evidence files and current
task-gate logs. Do not edit files, run Git writes, select another task or accept on
the basis of prose alone.

Return one JSON object conforming to `schemas/review.schema.json`, with no prose
before or after it.

- `ACCEPT`: the exact task gate is green, the implementation materially satisfies
  the task, tests are meaningful and changed paths are within scope. Set
  `next_action` to `Advance to the next task.`.
- `REVISE`: local code changes can resolve the remaining defect. Provide one precise
  next action suitable for OpenCode.
- `BLOCKED`: an external prerequisite prevents completion.
