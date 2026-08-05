# R4R hierarchical task queues

The launcher chooses an independent plan and progress file from
`config/r4r-agents.json`.

Ring reads `.opencode/task-plan.hierarchy.json` before proposing work. Ring coordinates
but never edits code. The implementation level determines the owner:

- Level 1: LP, six-month junior calibration.
- Level 2: PC, two-year developer calibration.
- Level 3: SURGICAL Codex, five-year senior calibration, through OpenCode on
  `agent/opencode-dual-surgical`.

SURGICAL Codex reviews every level-1 and level-2 result. The ten-year-calibrated Ring
lead checks prioritization, dependencies, ownership and evidence but does not replace
the technical code review.

## Queue ownership

- **PC/backend** uses `.opencode/task-plan.backend.json` and may not edit
  `frontend/**`.
- **LP/frontend** uses `.opencode/task-plan.frontend.json` and may edit only
  `frontend/**` and `docs/frontend/**`.
- Both controllers keep separate progress, memory, control and runtime evidence.
  Never launch two controllers for the same destination.

## Subtask contract

Each plan item is a commit-sized subtask:

1. one bounded objective;
2. one implementation level and one assigned role;
3. explicit dependencies and one canonical `allowed_paths` set;
4. a short ordered implementation recipe;
5. one exact deterministic gate;
6. one SURGICAL Codex decision;
7. one controller-owned closing commit.

Level-1 packages target one or two related files and 15–35 minutes. Level-2 packages
target one component or layer and 30–60 minutes. Level-3 packages may cross layers only
when decomposition would make the change unsafe and target 45–90 minutes. Ring must
split any package that exceeds these limits before dispatch.

Target 45–70 minutes of useful model work. The hard session ceiling is 90 minutes
(`5400` seconds). A session timeout preserves evidence and the working tree; it does
not mark the task complete.

A task that combines independent concerns must be split before execution. Typical
separate concerns are CLI contract, Spring lifecycle, typed failure classification,
real child-process proof, DOM behavior, citations, accessibility and final validation.

## Commit progression

OpenCode/Qwen3 and Codex never write Git history. The deterministic controller:

1. runs the exact gate;
2. rejects whitespace errors before expensive work;
3. creates a task-scoped checkpoint when the gate is green and a product diff exists;
4. requests SURGICAL Codex review through OpenCode;
5. creates the closing commit only after gate `0` and Codex `ACCEPT`;
6. advances to the next pending subtask.

A checkpoint preserves useful compilable work but does not complete the task. LP and
PC must escalate rather than widen a package beyond its declared implementation level.
