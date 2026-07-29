# Codex planning contract

Plan exactly one selected repository task in read-only mode. Inspect the current
repository, `AGENTS.md`, the parent task, the selected task, current memory and the
latest failing gate evidence. Do not edit files, run Git writes, select another task
or broaden scope.

Return one JSON object conforming to `schemas/plan.schema.json`, with no prose before
or after it.

- `READY`: the task can be implemented locally. Provide a short ordered list of
  concrete instructions and the most relevant paths.
- `BLOCKED`: an external prerequisite prevents implementation. State the exact
  prerequisite; do not use BLOCKED for ordinary code failures.
