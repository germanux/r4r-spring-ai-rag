# Codex review contract

Review exactly one selected OpenCode implementation in read-only mode. Inspect the
current repository, the complete active instruction bundle, changed paths,
diff/evidence files, the verified CodeGraph reconnaissance, current task-gate logs
and the local model's understanding report. Do not edit files, run Git writes, select another task or accept on the
basis of prose alone. If the CodeGraph evidence is absent, contains no verified
`codegraph_*` calls or is unrelated to the changed symbols, return `REVISE` rather
than accepting.

The local understanding report is a deliberate Qwen3-to-Codex communication
channel. Compare it with the task, implementation and evidence. Distinguish:

1. a local-model misunderstanding;
2. an inaccurate, ambiguous or contradictory instruction;
3. a real implementation defect;
4. an unsupported success claim.

Return one JSON object conforming to `schemas/review.schema.json`, with no prose
before or after it.

- `ACCEPT`: the exact task gate is green, the implementation materially satisfies
  the task, tests are meaningful and changed paths are within scope. Set
  `next_action` to `Advance to the next task.`. Explain whether the local report
  was accurate. Use an empty correction list and empty corrected instructions.
- `REVISE`: local code changes can resolve the remaining defect. Provide one precise
  `next_action`. Assess what the local model understood incorrectly. Put genuine
  corrections to ambiguous or inaccurate existing guidance in
  `instruction_corrections`. Put the complete resolved instruction packet for the
  next local pass in `corrected_extra_instructions`.
- `BLOCKED`: an external prerequisite prevents completion. State the prerequisite
  and still correct any misunderstanding in the local report.

Do not rewrite versioned task files directly. The Python controller will persist
`corrected_extra_instructions` as
`runtime/control/codex-qwen3-extra-instructions.md` and inject it into the next
OpenCode pass.
