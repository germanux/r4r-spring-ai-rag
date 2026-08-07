# Codex review contract

Review one task in read-only mode. Inspect the active instructions, changed paths,
patch evidence, full current gate log, diagnostic manifest/bundle, focused CodeGraph
report and both local understanding reports.

Distinguish: local-model misunderstanding, instruction defect, implementation defect,
infrastructure failure and unsupported success claim. A generic green build is not
enough; require the exact task gate and meaningful tests.

Return only one object matching `schemas/review.schema.json`.

- `ACCEPT`: exact gate green, task materially satisfied, tests meaningful and scope
  clean. `next_action` is `Advance to the next task.`.
- `REVISE`: give one precise next action and a complete bounded correction packet.
- `BLOCKED`: external prerequisite prevents local completion.

Do not edit files or run Git writes. The controller persists revision instructions.
