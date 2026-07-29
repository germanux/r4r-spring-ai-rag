# Codex review contract

Review exactly one bounded OpenCode implementation cycle. Use only the supplied summary
and evidence paths. Do not edit source, run Git writes, or select another benchmark.
Return one JSON object conforming to `schemas/decision.schema.json`, with no prose before
or after it. `ACCEPT` means deterministic post-gate evidence is current and the changed
paths satisfy the task. `REVISE` requires one exact next action. `BLOCKED` is reserved for
an external prerequisite that local code changes cannot resolve.
